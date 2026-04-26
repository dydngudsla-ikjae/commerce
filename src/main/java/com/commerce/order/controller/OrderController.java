package com.commerce.order.controller;

import com.commerce.global.jwt.AuthMember;
import com.commerce.order.dto.CreateOrderRequest;
import com.commerce.order.dto.OrderDetailResponse;
import com.commerce.order.dto.OrderResponse;
import com.commerce.order.service.OrderService;
import com.commerce.product.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(authMember.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<OrderResponse> pay(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long id) {
        OrderResponse response = orderService.pay(authMember.id(), id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long id) {
        OrderResponse response = orderService.cancel(authMember.id(), id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<OrderResponse> response = orderService.getMyOrders(authMember.id(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long id) {
        OrderDetailResponse response = orderService.getOrderDetail(authMember.id(), id);
        return ResponseEntity.ok(response);
    }
}