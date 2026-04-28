package com.commerce.order.scheduler;

import com.commerce.order.domain.OrderStatus;
import com.commerce.order.repository.OrderRepository;
import com.commerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OrderExpiryScheduler {

    private static final int PENDING_EXPIRY_MINUTES = 30;

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60_000)
    public void expireOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(PENDING_EXPIRY_MINUTES);
        orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, threshold)
                .forEach(order -> orderService.expireOrder(order.getId()));
    }
}