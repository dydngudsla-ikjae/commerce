package com.commerce.order.scheduler;

import com.commerce.order.domain.Order;
import com.commerce.order.domain.OrderStatus;
import com.commerce.order.repository.OrderRepository;
import com.commerce.order.service.OpsAlertService;
import com.commerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRetryScheduler {

    private static final int RETRY_INTERVAL_MINUTES = 5;
    private static final int MAX_RETRY_COUNT = 10;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OpsAlertService opsAlertService;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = 60_000)
    public void retryPayments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(RETRY_INTERVAL_MINUTES);
        List<Order> orders = orderRepository.findRetryablePaymentInProgressOrders(
                OrderStatus.PAYMENT_IN_PROGRESS, threshold);

        for (Order order : orders) {
            try {
                processRetry(order);
            } catch (Exception e) {
                log.warn("결제 재시도 처리 실패: orderId={}", order.getId(), e);
            }
        }
    }

    public void processRetry(Order order) {
        Long orderId = order.getId();

        try {
            orderService.confirmPayment(orderId);
            log.info("결제 재시도 성공: orderId={}", orderId);
        } catch (Exception e) {
            log.warn("결제 재시도 실패: orderId={}, retryCount={}", orderId, order.getRetryCount(), e);
            boolean exhausted = incrementRetryAndMaybeMarkFailed(orderId);

            if (exhausted) {
                opsAlertService.alertPaymentFailed(orderId);
                log.error("결제 실패 확정 (retry 소진): orderId={}", orderId);
            }
        }
    }

    private boolean incrementRetryAndMaybeMarkFailed(Long orderId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Order o = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));
            if (o.getStatus() != OrderStatus.PAYMENT_IN_PROGRESS) {
                return false;
            }
            o.incrementRetry(LocalDateTime.now());
            if (o.getRetryCount() >= MAX_RETRY_COUNT) {
                o.markPaymentFailed();
                return true;
            }
            return false;
        }));
    }
}