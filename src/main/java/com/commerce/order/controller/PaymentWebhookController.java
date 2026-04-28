package com.commerce.order.controller;

import com.commerce.order.dto.OrderResponse;
import com.commerce.order.dto.PaymentWebhookRequest;
import com.commerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final OrderService orderService;

    @PostMapping("/payment")
    public ResponseEntity<OrderResponse> paymentWebhook(@RequestBody PaymentWebhookRequest request) {
        OrderResponse response = orderService.confirmPayment(request.orderId());
        return ResponseEntity.ok(response);
    }
}