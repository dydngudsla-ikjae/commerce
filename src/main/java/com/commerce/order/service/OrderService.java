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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

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
        long jitter = (long) (Math.random() * base);
        try {
            Thread.sleep(base + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public OrderResponse pay(Long memberId, Long orderId) {
        // 1. 검증 (트랜잭션 없음)
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }

        // 2. 외부 PG API 호출 (트랜잭션 없음)
        paymentGateway.charge(orderId, order.getTotalAmount());

        // 3. 결제 확정
        return confirmPayment(orderId);
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

        if (order.getStatus() == OrderStatus.PAID) {
            return OrderResponse.from(order);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }

        order.pay();

        List<StockDeductionCommand> commands = order.getItems().stream()
                .map(item -> new StockDeductionCommand(item.getVariantId(), item.getQuantity()))
                .toList();
        stockDeductionStrategy.confirm(commands);

        return OrderResponse.from(order);
    }

    public OrderResponse cancel(Long memberId, Long orderId) {
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