# Commerce

JPA 낙관적 락 + 트랜잭션 분리 + 스케줄러 기반 자가 복구로 **동시 주문/결제/취소 정합성**을 다룬 커머스 백엔드 토이 프로젝트.

> 본 README는 포트폴리오 리뷰용 요약 문서다. 각 도메인의 상세 정책과 의사결정 기록은 [work/](./work) 폴더의 마크다운 문서를 참조한다.

---

## 1. 프로젝트 목적

흔한 CRUD 커머스가 아니라, 실서비스에서 가장 자주 깨지는 두 가지 정합성 문제를 정면으로 다루는 데에 집중했다.

1. **동시 주문 시 재고 oversell 방지** — 같은 SKU에 대한 동시 요청이 재고 상한을 넘지 않도록 보장
2. **PG ↔ DB 사이 장애 복구** — PG 호출은 성공했지만 DB 저장이 실패했을 때, 또는 그 반대일 때 자동으로 정합 상태로 수렴

---

## 2. 기술 스택

| 구분 | 사용 기술 |
|------|----------|
| Language | Java 21 |
| Framework | Spring Boot 3.3.4, Spring Security, Spring Data JPA |
| DB | PostgreSQL 15 (운영), H2 (테스트) |
| Migration | Flyway |
| Auth | JWT (Access 30분 / Refresh 7일, DB rotation) |
| Docs | springdoc-openapi (Swagger UI) |
| Build | Gradle |

---

## 3. 아키텍처

레이어드 아키텍처(`controller → service → repository`)를 채택했다. 토이 프로젝트 규모에서 DDD 스타일(`application / domain / infrastructure`)은 과한 오버헤드라고 판단했다.

```
com.commerce
├── global         # Security, JWT, ExceptionHandler, Config
├── member         # 회원, 인증, JWT
├── product        # 상품, Variant(SKU), 재고 차감 전략
└── order          # 주문, 결제, 취소, 스케줄러
    ├── controller
    ├── service
    ├── repository
    ├── domain
    ├── dto
    └── scheduler  # PaymentRetry, CancelRetry, PaymentInquiry, OrderExpiry
```

---

## 4. 핵심 도메인 모델

### 4.1 재고 예약 모델

`available_stock = stock - reserved_stock` 으로 가용 재고를 정의한다. 주문 생성 시점에 즉시 차감하지 않고 `reserved_stock`을 증가시켜 예약하고, 결제 완료 시점에 두 값을 동시에 감소시켜 확정한다.

| 단계 | 연산 | stock | reserved_stock | available_stock |
|------|------|-------|----------------|-----------------|
| 주문 생성 | `reserve(n)`  | -      | +n     | -n     |
| 결제 완료 | `confirm(n)`  | -n     | -n     | 변화 없음 |
| 주문 취소 | `release(n)`  | -      | -n     | +n     |
| 결제 후 환불 | `refund(n)` | +n   | -      | +n     |

이 모델로 얻는 것:

- 결제 전 사용자에게 재고를 잡아두면서도, 결제 실패 시 즉시 다른 사용자에게 풀 수 있다
- 결제 확정 시 `stock`과 `reserved_stock`을 같이 감소시켜 `available_stock` 불변량이 유지된다

### 4.2 가격 스냅샷

`OrderItem`은 주문 시점의 `product_name`, `price`를 별도 컬럼으로 저장한다. 이후 상품 가격이 바뀌어도 기존 주문 데이터는 영향받지 않는다.

---

## 5. 주문 상태 머신

```
PENDING ─────────────────────────────────────→ CANCELLED  (사용자 취소 / 만료 스케줄러)
   │
   └─ startPayment() ─→ PAYMENT_IN_PROGRESS
                          │
                          ├─ PG 명시 실패          ─→ CANCELLED         (재고 즉시 해제)
                          ├─ confirmPayment 성공   ─→ PAID
                          └─ retry 소진            ─→ PAYMENT_FAILED    (관리자 수동 처리 대상)
                                                       │
                                                       ├─ forceConfirm ─→ PAID
                                                       └─ forceCancel  ─→ CANCELLED
PAID
   └─ startCancellation() ─→ CANCEL_IN_PROGRESS  (환불 선점)
                               └─ completeCancel ─→ CANCELLED         [final]
                                  (실패 시 CANCEL_IN_PROGRESS 유지 → CancelRetryScheduler가 재시도)
```

전이의 권한은 `Order` 도메인 객체에 캡슐화되어 있다. 잘못된 전이를 시도하면 `ORDER_INVALID_STATUS` 예외를 던지며, 도메인 외부에서는 상태 필드를 직접 건드릴 수 없다.

---

## 6. 동시성 처리

### 6.1 낙관적 락 + 지수 백오프 재시도

- `Order`, `ProductVariant` 양쪽에 `@Version` 컬럼을 둔다
- 충돌(`OptimisticLockingFailureException`) 발생 시, 트랜잭션 바깥의 재시도 루프가 `TransactionTemplate.execute()`로 **새 트랜잭션을 열어** 다시 시도한다 (재시도 시마다 최신 version을 새로 읽기 위함)
- 최대 3회, `100ms → 200ms → 400ms` 지수 백오프
- `ThreadLocalRandom`으로 지터 추가 — thundering herd 방지

```java
for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
    try {
        return transactionTemplate.execute(status -> doCreateOrder(memberId, request));
    } catch (RuntimeException e) {
        if (!stockDeductionStrategy.isRetryable(e)) {
            throw e;
        }
        if (attempt < MAX_RETRIES - 1) applyBackoff(attempt);
    }
}
throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
```

`isRetryable()`은 예외의 cause chain을 최대 깊이 10까지 순회하면서 `OptimisticLockingFailureException` 또는 `StaleObjectStateException`을 탐지한다. Hibernate가 다른 예외로 wrap해도 잡힌다.

### 6.2 데드락 방지

다수 Variant 주문 시 `variant_id` 오름차순으로 정렬한 뒤 락을 잡는다. 모든 트랜잭션이 같은 순서로 잡으면 순환 대기가 생기지 않는다.

### 6.3 재고 차감 전략 패턴

`StockDeductionStrategy` 인터페이스로 추상화. 기본 구현체는 `OptimisticLockStockStrategy` (`@Primary`), 핫딜 같은 고경합 시나리오에서는 다른 구현체(AtomicUpdate / Pessimistic / Redis 분산 락)로 교체 가능하도록 설계했다.

---

## 7. 결제 정합성 — TX 분리와 자가 복구

결제는 단일 트랜잭션이 아니라 **3개의 트랜잭션 + 1개의 외부 호출**로 쪼개져 있다.

```
TX1: PENDING → PAYMENT_IN_PROGRESS (선점, 소유자 검증, totalAmount 캡처)
        │
[외부]: pg.charge(orderId, amount)   ← 트랜잭션 밖. 절대 트랜잭션 안에서 호출하지 않는다.
        │
TX2: pgTransactionId 저장             ← 낙관적 락 재시도 루프 포함
        │
TX3: confirmPayment() — 재고 확정 + PAID
        └ 실패 시: PAYMENT_IN_PROGRESS 유지, 스케줄러에 위임
```

이렇게 쪼갠 이유는 **각 단계 사이에 서버가 죽었을 때 복구 가능 상태로 수렴시키기 위함**이다. 가능한 모든 실패 지점:

| 실패 지점 | DB 상태 | pgTransactionId | 복구 주체 |
|-----------|--------|-----------------|----------|
| TX1 실패 | PENDING | null | 사용자 재시도 |
| pg.charge() 예외 throw | PAYMENT_IN_PROGRESS | null | **PaymentInquiryScheduler** |
| pg.charge() 직후 서버 다운 | PAYMENT_IN_PROGRESS | null | **PaymentInquiryScheduler** |
| TX2 도중 서버 다운 | PAYMENT_IN_PROGRESS | null | **PaymentInquiryScheduler** |
| TX2 직후 서버 다운 | PAYMENT_IN_PROGRESS | 저장됨 | **PaymentRetryScheduler** |
| TX3 실패 | PAYMENT_IN_PROGRESS | 저장됨 | **PaymentRetryScheduler** |

핵심은 `pgTransactionId`의 NULL 여부로 PG 청구 여부를 가르고, 각각 다른 복구 경로를 둔 것이다.

### 7.1 PG 멱등키

**orderId 자체를 PG 멱등키로** 사용한다. 동일 `orderId`로 `charge()`나 `refund()`를 재호출해도 PG는 동일 거래로 인식해 이중 청구/환불을 방지한다는 계약을 전제로 한다 (`FakePaymentGateway` 구현체가 이 동작을 시뮬레이션한다).

별도 `payment_attempt_id` 컬럼을 따로 두는 방안도 검토했으나 토이 프로젝트 범위에서는 orderId로 충분하다고 판단했다.

### 7.2 도메인 멱등성

- `Order.savePgTransactionId(id)` — 같은 값이면 no-op, 다른 값이면 `PG_TRANSACTION_ID_MISMATCH` 예외
- `Order.confirmPaid()` — 이미 PAID면 no-op
- `Order.completeCancel()` — 이미 CANCELLED면 no-op
- `AuditLog.idempotency_key` — 관리자 액션 (`FORCE_CONFIRM:{orderId}`) 중복 방지에 unique 제약

스케줄러가 같은 주문을 두 번 호출해도 안전하다.

---

## 8. 자가 복구 스케줄러

총 4개의 스케줄러가 각각 다른 잔여 상태를 처리한다. 모두 `@Scheduled(fixedDelay = 60_000)`로 1분마다 동작.

| 스케줄러 | 대상 | 동작 |
|---------|------|------|
| **OrderExpiryScheduler** | `PENDING` 30분 경과 | 자동 취소, 예약 재고 해제 |
| **PaymentRetryScheduler** | `PAYMENT_IN_PROGRESS` + `pg_transaction_id IS NOT NULL` + 5분 경과 | `confirmPayment()` 재시도. 10회 소진 시 `PAYMENT_FAILED` 전환 + 운영 알림 |
| **PaymentInquiryScheduler** | `PAYMENT_IN_PROGRESS` + `pg_transaction_id IS NULL` + 5분 경과 | PG에 `inquiry(orderId)` 호출 → SUCCESS면 복구 / FAIL이면 취소 / UNKNOWN이면 운영팀 알림 |
| **CancelRetryScheduler** | `CANCEL_IN_PROGRESS` + 5분 경과 | `pg.refund()` 재호출(멱등) + `completeCancel()` |

`PaymentInquiryScheduler`는 이 프로젝트에서 가장 신경 쓴 부분이다. PG는 호출됐는데 우리 DB가 그 사실을 모르는 상태(`pgTransactionId IS NULL`)를 사람 개입 없이 자동으로 정합 상태로 복구한다.

---

## 9. 관리자 강제 처리 경로

자동 복구 한계(`PAYMENT_FAILED`, PG inquiry `UNKNOWN`)에 도달한 주문은 관리자가 처리한다.

```
1. POST /api/v1/admin/orders/{id}/verify-payment
   → PG 상태 조회 → PaymentVerification 저장 (SUCCESS / FAIL / UNKNOWN)

2-A. POST /api/v1/admin/orders/{id}/force-confirm  (PG가 SUCCESS여야 가능)
2-B. POST /api/v1/admin/orders/{id}/force-cancel   (PG가 FAIL이어야 가능)
```

**불변 규칙:**

- `verify-payment` 없이 `force-*` 호출 → `PAYMENT_VERIFY_REQUIRED`
- PG 상태와 액션 불일치 → `PAYMENT_VERIFY_MISMATCH` (성공한 결제를 강제 취소하거나 실패한 결제를 강제 확정하는 사고 방지)
- 모든 관리자 액션은 `reason` 필수 + `AuditLog` 기록
- `AuditLog.idempotency_key` unique 제약으로 중복 처리 방지

---

## 10. 인증 / 보안

### 10.1 JWT 구조

- Access Token: 30분, Stateless (서버 측 무효화 불가)
- Refresh Token: 7일, DB 저장, `member_id` unique 제약으로 1:1 보장
- Refresh Rotation: 기존 토큰 DELETE → 새 토큰 INSERT (중복 사용 차단)
- Stateless 유지 위해 Access Token은 로그아웃 시에도 무효화하지 않음 (만료까지 유효)

### 10.2 로그인 실패 잠금

- 실패 시 `UPDATE login_fail_count = login_fail_count + 1`로 **DB 원자 연산**
- 5회 실패 → `LOCKED`, 관리자 수동 해제
- 인증 실패는 원인(없는 사용자 / 비번 오류 / LOCKED)을 모두 `AUTH_INVALID`로 통합 — 정보 노출 방지

### 10.3 회원 탈퇴

- 물리 삭제 X, `status = DELETED` + 이메일 `user@x.com_deleted_{epochMilli}`로 변조
- 동일 이메일로 재가입 가능 + 기존 주문 데이터 유지

### 10.4 Webhook 엔드포인트

`/webhook/**`는 인증 없이 접근 가능 (`SecurityConfig`의 `permitAll`). PG가 외부에서 호출하기 때문이다. `confirmPayment()`의 멱등성으로 중복 호출 안전.

---

## 11. 글로벌 예외 처리

`GlobalExceptionHandler`에서 다음을 처리:

| 예외 | 응답 |
|------|------|
| `BusinessException` | `ErrorCode`의 status + 메시지 |
| `OptimisticLockingFailureException` | `409 CONCURRENT_CONFLICT` |
| `MethodArgumentNotValidException` | `400 INVALID_INPUT` + 첫 번째 검증 메시지 |
| `Exception` (catch-all) | `500 INTERNAL_SERVER_ERROR` + 스택 트레이스는 로그에만 |

마지막 catch-all로 스택 트레이스 노출을 차단한다.

---

## 12. 테스트

`src/test`에 핵심 시나리오 위주의 통합 테스트가 있다. **MockMvc + H2 + 실제 Bean 그래프** 구성.

- `OrderControllerTest` — 주문 생성/결제/취소 API end-to-end
- `OrderConcurrencyTest` — 동시 주문 oversell 방지, 동시 취소 멱등성, `@Version` 충돌 시 1건만 성공 검증
- `PaymentRetrySchedulerTest` — 재시도 카운트 증가, 소진 시 `PAYMENT_FAILED` 전환
- `CancelRetrySchedulerTest` — 환불 재시도, 멱등성
- `AdminPaymentControllerTest` — verify → force-confirm/cancel 흐름, 불변 규칙 위반 케이스
- `ProductControllerTest`, `AuthControllerTest`

---

## 13. 실행 방법

### 13.1 환경 변수

```bash
JWT_SECRET=<base64 32바이트 이상>
```

### 13.2 DB 준비

```bash
docker run -d --name commerce-pg \
  -e POSTGRES_DB=commerce \
  -e POSTGRES_USER=commerce \
  -e POSTGRES_PASSWORD=commerce1234 \
  -p 5432:5432 postgres:15
```

### 13.3 실행

```bash
./gradlew bootRun
```

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Flyway가 `V1 ~ V8` 마이그레이션을 자동 적용
- 관리자 시드 계정: `admin1@example.com / Admin1234!`

### 13.4 테스트

```bash
./gradlew test
```

---

## 14. 설계 요약

이 프로젝트에서 강조하고 싶었던 의사결정:

1. **트랜잭션 경계는 코드에서 명시적으로 드러나야 한다** — `@Transactional`을 메서드에 붙이는 대신 `TransactionTemplate`을 직접 써서 "재시도 루프는 트랜잭션 바깥", "PG 외부 호출은 트랜잭션 바깥"이 한눈에 보이도록 했다
2. **외부 호출과 DB 저장 사이의 모든 실패 지점을 명시적으로 분류한다** — 각 지점에 다른 복구 경로를 두고, 자동 처리 한계는 관리자 경로로 흘려보낸다
3. **모든 상태 전이는 도메인 객체에 캡슐화된 멱등 연산이어야 한다** — 스케줄러와 webhook이 동시에 호출해도 깨지지 않도록
4. **추상화는 미래의 요구가 아니라 현재의 교체 가능성을 위해서만 도입한다** — 재고 차감 전략 패턴은 핫딜 시나리오라는 구체적 요구에서 출발했다. 사용처가 하나뿐이면 인터페이스를 만들지 않는다

---

## 15. 한계와 후속 작업

토이 프로젝트 범위에서 제외한 항목:

- 분산 환경 — 단일 인스턴스 가정. 다중 인스턴스로 가면 스케줄러에 분산 락이 필요
- 배송 도메인 — `PAID` 이후 사용자 취소는 무조건 허용 (실제로는 `SHIPPED` 이후 취소 불가)
- 반품/부분 환불
- 결제 수단 분기 — 카드/계좌이체 등 PG 라우팅 없음. 단일 `PaymentGateway` 인터페이스
- 부하 테스트(k6 등) — 정합성은 JUnit으로 검증, 처리량 지표는 측정하지 않음
- 관리자 주문 전체 조회 API
- 카테고리 삭제/이동