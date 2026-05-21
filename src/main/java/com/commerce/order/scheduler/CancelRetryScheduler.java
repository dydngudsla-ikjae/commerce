package com.commerce.order.scheduler;

import com.commerce.order.domain.Order;
import com.commerce.order.domain.OrderStatus;
import com.commerce.order.repository.OrderRepository;
import com.commerce.order.service.OrderService;
import com.commerce.order.service.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelRetryScheduler {

    private static final int RETRY_INTERVAL_MINUTES = 5;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PaymentGateway paymentGateway;

    @Scheduled(fixedDelay = 60_000)
    public void retryCancellations() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(RETRY_INTERVAL_MINUTES);
        List<Order> orders = orderRepository.findCancelInProgressOrdersBefore(
                OrderStatus.CANCEL_IN_PROGRESS, threshold);

        for (Order order : orders) {
            try {
                processRetry(order);
            } catch (Exception e) {
                log.warn("환불 재시도 처리 실패: orderId={}", order.getId(), e);
            }
        }
    }

    public void processRetry(Order order) {
        Long orderId = order.getId();
        try {
            paymentGateway.refund(orderId, order.getPgTransactionId());
            orderService.completeCancel(orderId);
            log.info("환불 재시도 성공: orderId={}", orderId);
        } catch (Exception e) {
            log.warn("환불 재시도 실패: orderId={}", orderId, e);
        }
    }
}