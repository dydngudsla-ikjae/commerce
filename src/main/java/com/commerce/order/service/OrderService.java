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
import com.commerce.product.repository.ProductVariantRepository;
import com.commerce.product.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;

    @Transactional
    public OrderResponse createOrder(Long memberId, CreateOrderRequest request) {
        // 1. 모든 Variant를 product JOIN FETCH로 한 번에 조회
        List<Long> variantIds = request.items().stream()
                .map(item -> item.variantId())
                .toList();

        Map<Long, ProductVariant> variantMap = productVariantRepository.findByIdInWithProduct(variantIds)
                .stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        // 2. Variant 상태 검증 및 total_amount 계산
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

        // 3. 재고 차감 (atomic, clearAutomatically로 1차 캐시 초기화됨)
        for (var itemRequest : request.items()) {
            int updated = productVariantRepository.decreaseStock(itemRequest.variantId(), itemRequest.quantity());
            if (updated == 0) {
                throw new BusinessException(ErrorCode.OUT_OF_STOCK);
            }
        }

        // 4. 주문 생성 및 OrderItem 스냅샷 저장 (variantMap은 detach 후에도 이미 로딩된 값 사용)
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

    @Transactional
    public OrderResponse pay(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }

        order.pay();
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }

        // 재고 복구 먼저 (JPQL flush/clear 전에 items 로딩)
        List<OrderItem> items = order.getItems();
        for (OrderItem item : items) {
            productVariantRepository.increaseStock(item.getVariantId(), item.getQuantity());
        }

        // 재고 복구 후 상태 변경 (JPQL clear 이후에도 dirty checking 보장)
        order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        order.cancel();

        return OrderResponse.from(order);
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
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        return OrderDetailResponse.from(order);
    }
}