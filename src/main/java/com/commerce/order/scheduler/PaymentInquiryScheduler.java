package com.commerce.order.scheduler;

import com.commerce.order.domain.Order;
import com.commerce.order.domain.OrderStatus;
import com.commerce.order.repository.OrderRepository;
import com.commerce.order.service.OpsAlertService;
import com.commerce.order.service.OrderService;
import com.commerce.order.service.PaymentGateway;
import com.commerce.order.service.PgInquiryResult;
import com.commerce.order.service.PgPaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * pgTransactionId IS NULL + PAYMENT_IN_PROGRESS 주문 복구 스케줄러.
 *
 * 대상: PG 호출 전 서버 다운(TX1 후 pg.charge() 미실행) 또는
 *       PG 호출 성공 후 TX2 전 서버 다운(pgTransactionId DB 저장 실패) 주문.
 *
 * orderId를 멱등키로 PG에 inquiry하여 실제 결제 여부를 확인한다.
 *   - SUCCESS  → pgTransactionId 저장 + confirmPayment (재고 확정 + PAID)
 *   - FAIL     → 재고 해제 + CANCELLED
 *   - UNKNOWN  → 운영팀 알림 (수동 개입 필요)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentInquiryScheduler {

    // 너무 빠른 inquiry는 PG 측 처리 완료 전일 수 있으므로 충분한 대기 시간 확보
    private static final int INQUIRY_DELAY_MINUTES = 5;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PaymentGateway paymentGateway;
    private final OpsAlertService opsAlertService;

    @Scheduled(fixedDelay = 60_000)
    public void inquireOrphanedPayments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(INQUIRY_DELAY_MINUTES);
        List<Order> orders = orderRepository.findOrphanedPaymentInProgressOrders(
                OrderStatus.PAYMENT_IN_PROGRESS, threshold);

        for (Order order : orders) {
            try {
                processInquiry(order);
            } catch (Exception e) {
                log.warn("PG inquiry 처리 실패: orderId={}", order.getId(), e);
            }
        }
    }

    // 테스트에서 직접 호출하기 위해 public
    public void processInquiry(Order order) {
        Long orderId = order.getId();
        PgInquiryResult result = paymentGateway.query(orderId);

        switch (result.status()) {
            case SUCCESS -> {
                // PG에서 결제 확인: pgTransactionId 저장 후 재고 확정 + PAID
                orderService.recoverPayment(orderId, result.pgTransactionId());
                log.info("PG inquiry 복구 성공 (PAID 전환): orderId={}", orderId);
            }
            case FAIL -> {
                // PG에서 결제 안 됐음 확인: 재고 해제 + CANCELLED
                orderService.cancelByPgFailureWithRetry(orderId);
                log.info("PG inquiry 취소 처리 (CANCELLED 전환): orderId={}", orderId);
            }
            case UNKNOWN -> {
                // PG도 결제 여부를 모름 — 자동 처리 불가, 운영팀 수동 개입 필요
                opsAlertService.alertPaymentUnknown(orderId);
                log.error("PG inquiry 불명 — 수동 개입 필요: orderId={}", orderId);
            }
        }
    }
}