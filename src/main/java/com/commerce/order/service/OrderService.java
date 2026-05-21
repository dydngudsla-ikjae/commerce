package com.commerce.order.service;

import com.commerce.global.exception.BusinessException;
import com.commerce.global.exception.ErrorCode;
import com.commerce.order.domain.Order;
import com.commerce.order.domain.OrderItem;
import com.commerce.order.domain.OrderStatus;
import com.commerce.order.dto.CreateOrderRequest;
import com.commerce.order.dto.OrderDetailResponse;
import com.commerce.order.dto.OrderResponse;
import com.commerce.order.repository.OrderRepository;
import com.commerce.product.domain.ProductVariant;
import com.commerce.product.domain.VariantStatus;
import com.commerce.product.dto.PageResponse;
import com.commerce.product.repository.ProductVariantRepository;
import com.commerce.product.service.StockDeductionCommand;
import com.commerce.product.service.StockDeductionStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    // 낙관적 락 충돌(OptimisticLockingFailureException) 시 지수 백오프 재시도
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY_MS = 100L;
    private static final double MULTIPLIER = 2.0;

    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;
    private final StockDeductionStrategy stockDeductionStrategy;
    private final TransactionTemplate transactionTemplate;
    private final PaymentGateway paymentGateway;

    public OrderResponse createOrder(Long memberId, CreateOrderRequest request) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status -> doCreateOrder(memberId, request));
            } catch (RuntimeException e) {
                if (!stockDeductionStrategy.isRetryable(e)) {
                    throw e;
                }
                if (attempt < MAX_RETRIES - 1) {
                    applyBackoff(attempt);
                }
            }
        }
        throw new BusinessException(ErrorCode.OUT_OF_STOCK);
    }

    private OrderResponse doCreateOrder(Long memberId, CreateOrderRequest request) {
        // fetch join으로 product 한 번에 조회 (N+1 방지)
        List<Long> variantIds = request.items().stream()
                .map(item -> item.variantId())
                .toList();

        Map<Long, ProductVariant> variantMap = productVariantRepository.findByIdInWithProduct(variantIds)
                .stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        long totalAmount = 0L;
        for (var itemRequest : request.items()) {
            ProductVariant variant = variantMap.get(itemRequest.variantId());
            if (variant == null) {
                throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND);
            }
            if (variant.getStatus() != VariantStatus.ON_SALE) {
                throw new BusinessException(ErrorCode.VARIANT_SOLD_OUT);
            }
            totalAmount += variant.getPrice() * itemRequest.quantity();
        }

        List<StockDeductionCommand> commands = request.items().stream()
                .map(item -> new StockDeductionCommand(item.variantId(), item.quantity()))
                .toList();
        // 재고 예약 → Order 저장 순서. 반대로 하면 재고 예약 실패 시 이미 저장된 주문이 남는다.
        stockDeductionStrategy.reserve(commands);

        Order order = Order.create(memberId, totalAmount);
        orderRepository.save(order);

        for (var itemRequest : request.items()) {
            ProductVariant variant = variantMap.get(itemRequest.variantId());
            OrderItem item = OrderItem.create(order, itemRequest.variantId(),
                    variant.getProduct().getName(), variant.getPrice(), itemRequest.quantity());
            order.getItems().add(item);
        }

        return OrderResponse.from(order);
    }

    private void applyBackoff(int attempt) {
        long base = (long) (INITIAL_DELAY_MS * Math.pow(MULTIPLIER, attempt));
        // jitter: 동시 충돌한 스레드들이 같은 간격으로 재시도하면 계속 충돌하므로 무작위 편차 추가
        long jitter = (long) (Math.random() * base);
        try {
            Thread.sleep(base + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * TX1: 소유자 검증 + PENDING → PAYMENT_IN_PROGRESS + totalAmount 캡처
     * [외부]: pg.charge() 호출
     * TX2: pgTransactionId 저장
     * TX3: confirmPayment() — 재고 확정 + PAID
     *   └ 실패 시: PAYMENT_IN_PROGRESS 유지 (예외 삼킴, 스케줄러가 처리)
     */
    public OrderResponse pay(Long memberId, Long orderId) {
        // TX1: 소유자 검증 + PENDING → PAYMENT_IN_PROGRESS + totalAmount 캡처
        long capturedAmount = transactionTemplate.execute(status -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
            if (!order.getMemberId().equals(memberId)) {
                throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
            }
            order.startPayment();
            return order.getTotalAmount();
        });

        // [외부]: pg.charge() 호출 (트랜잭션 없음)
        PaymentResult pgResult;
        try {
            pgResult = paymentGateway.charge(orderId, capturedAmount);
        } catch (PgChargeException e) {
            // PG가 실패를 명시적으로 반환 → 재고 해제 + CANCELLED
            transactionTemplate.executeWithoutResult(status -> {
                Order order = orderRepository.findByIdWithItems(orderId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
                List<StockDeductionCommand> commands = order.getItems().stream()
                        .map(item -> new StockDeductionCommand(item.getVariantId(), item.getQuantity()))
                        .toList();
                order.cancelByPgFailure();
                stockDeductionStrategy.release(commands);
            });
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        } catch (Exception e) {
            // 예기치 않은 오류 (네트워크 타임아웃 등) — PG 청구 여부 불명확
            // pgTransactionId 미저장으로 인해 스케줄러 자동 재시도 대상에서 제외됨 → 관리자 수동 처리
            log.error("pg.charge() 예기치 않은 오류 — 수동 개입 필요. orderId={}", orderId, e);
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }

        // TX2: pgTransactionId 저장
        final String pgTransactionId = pgResult.transactionId();
        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
            order.savePgTransactionId(pgTransactionId);
        });

        // TX3: confirmPayment() — 재고 확정 + PAID
        // 실패 시 예외 삼킴 (스케줄러가 처리)
        try {
            return confirmPayment(orderId);
        } catch (Exception e) {
            log.warn("confirmPayment 실패 — 스케줄러가 재시도합니다. orderId={}", orderId, e);
            // PAYMENT_IN_PROGRESS 유지, 현재 상태 반환
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
            return OrderResponse.from(order);
        }
    }

    public OrderResponse confirmPayment(Long orderId) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status -> doConfirmPayment(orderId));
            } catch (RuntimeException e) {
                if (!stockDeductionStrategy.isRetryable(e)) {
                    throw e;
                }
                if (attempt < MAX_RETRIES - 1) {
                    applyBackoff(attempt);
                }
            }
        }
        throw new BusinessException(ErrorCode.OUT_OF_STOCK);
    }

    private OrderResponse doConfirmPayment(Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 이미 PAID 면 재고 confirm 없이 즉시 반환 — 스케줄러 중복 호출 안전
        if (order.getStatus() == OrderStatus.PAID) {
            return OrderResponse.from(order);
        }
        if (order.getStatus() != OrderStatus.PAYMENT_IN_PROGRESS
                && order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }

        order.confirmPaid();

        List<StockDeductionCommand> commands = order.getItems().stream()
                .map(item -> new StockDeductionCommand(item.getVariantId(), item.getQuantity()))
                .toList();
        stockDeductionStrategy.confirm(commands);

        return OrderResponse.from(order);
    }

    public OrderResponse cancel(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (order.getStatus() == OrderStatus.PAID) {
            return cancelPaid(memberId, orderId);
        }

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status -> doCancel(memberId, orderId));
            } catch (RuntimeException e) {
                if (!stockDeductionStrategy.isRetryable(e)) {
                    throw e;
                }
                if (attempt < MAX_RETRIES - 1) {
                    applyBackoff(attempt);
                }
            }
        }
        throw new BusinessException(ErrorCode.OUT_OF_STOCK);
    }

    private OrderResponse doCancel(Long memberId, Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        List<StockDeductionCommand> commands = order.getItems().stream()
                .map(item -> new StockDeductionCommand(item.getVariantId(), item.getQuantity()))
                .toList();

        order.cancel();
        stockDeductionStrategy.release(commands);

        return OrderResponse.from(order);
    }

    /**
     * TX1: 소유자 검증 + PAID → CANCEL_IN_PROGRESS (선점, @Version으로 1건만 성공)
     * [외부]: pg.refund() 호출 — 실패 시 REFUND_GATEWAY_ERROR, CANCEL_IN_PROGRESS 유지
     * TX2: CANCEL_IN_PROGRESS → CANCELLED + 재고 복구
     *   └ 실패 시: CANCEL_IN_PROGRESS 유지 (CancelRetryScheduler가 재시도)
     */
    private OrderResponse cancelPaid(Long memberId, Long orderId) {
        // TX1: PAID → CANCEL_IN_PROGRESS 선점
        String pgTransactionId = transactionTemplate.execute(status -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
            if (!order.getMemberId().equals(memberId)) {
                throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
            }
            order.startCancellation(); // PAID 아니면 ORDER_INVALID_STATUS
            return order.getPgTransactionId();
        });

        // [외부]: pg.refund() — 실패 시 CANCEL_IN_PROGRESS 유지, 스케줄러가 재시도
        try {
            paymentGateway.refund(orderId, pgTransactionId);
        } catch (Exception e) {
            log.error("pg.refund() 실패 — CancelRetryScheduler가 재시도합니다. orderId={}", orderId, e);
            throw new BusinessException(ErrorCode.REFUND_GATEWAY_ERROR);
        }

        // TX2: CANCEL_IN_PROGRESS → CANCELLED + 재고 복구
        return completeCancel(orderId);
    }

    public OrderResponse completeCancel(Long orderId) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status -> doCompleteCancel(orderId));
            } catch (RuntimeException e) {
                if (!stockDeductionStrategy.isRetryable(e)) {
                    throw e;
                }
                if (attempt < MAX_RETRIES - 1) {
                    applyBackoff(attempt);
                }
            }
        }
        throw new BusinessException(ErrorCode.OUT_OF_STOCK);
    }

    private OrderResponse doCompleteCancel(Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 이미 CANCELLED면 재고 복구 없이 즉시 반환 — 스케줄러 중복 호출 안전
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return OrderResponse.from(order);
        }

        order.completeCancel(); // CANCEL_IN_PROGRESS → CANCELLED

        List<StockDeductionCommand> commands = order.getItems().stream()
                .map(item -> new StockDeductionCommand(item.getVariantId(), item.getQuantity()))
                .toList();
        stockDeductionStrategy.refund(commands);

        return OrderResponse.from(order);
    }

    public void expireOrder(Long orderId) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> doExpireOrder(orderId));
                return;
            } catch (RuntimeException e) {
                if (!stockDeductionStrategy.isRetryable(e)) {
                    throw e;
                }
                if (attempt < MAX_RETRIES - 1) {
                    applyBackoff(attempt);
                }
            }
        }
        throw new BusinessException(ErrorCode.OUT_OF_STOCK);
    }

    private void doExpireOrder(Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 스케줄러가 조회 후 상태가 이미 바뀐 경우(사용자가 직전에 결제/취소) 조용히 스킵
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        List<StockDeductionCommand> commands = order.getItems().stream()
                .map(item -> new StockDeductionCommand(item.getVariantId(), item.getQuantity()))
                .toList();

        order.cancel();
        stockDeductionStrategy.release(commands);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(Long memberId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> result = orderRepository.findByMemberId(memberId, pageable);

        return new PageResponse<>(
                result.getContent().stream().map(OrderResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long memberId, Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        return OrderDetailResponse.from(order);
    }
}