package com.commerce.order;

import com.commerce.global.jwt.JwtProvider;
import com.commerce.order.domain.Order;
import com.commerce.order.domain.OrderStatus;
import com.commerce.order.dto.OrderResponse;
import com.commerce.order.repository.OrderItemRepository;
import com.commerce.order.repository.OrderRepository;
import com.commerce.order.scheduler.PaymentRetryScheduler;
import com.commerce.order.service.FakeOpsAlertService;
import com.commerce.order.service.FakePaymentGateway;
import com.commerce.product.dto.CategoryResponse;
import com.commerce.product.dto.ProductResponse;
import com.commerce.product.dto.VariantResponse;
import com.commerce.product.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentRetrySchedulerTest {

    @LocalServerPort
    private int port;

    private RestClient client;

    @Autowired private JwtProvider jwtProvider;
    @Autowired private PaymentRetryScheduler paymentRetryScheduler;
    @Autowired private FakePaymentGateway fakePaymentGateway;
    @Autowired private FakeOpsAlertService fakeOpsAlertService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private ProductVariantRepository productVariantRepository;
    @Autowired private ProductVariantOptionRepository productVariantOptionRepository;
    @Autowired private ProductOptionValueRepository productOptionValueRepository;
    @Autowired private ProductOptionRepository productOptionRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        fakePaymentGateway.reset();
        fakeOpsAlertService.reset();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productVariantOptionRepository.deleteAll();
        productVariantRepository.deleteAll();
        productOptionValueRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    // ==================== 헬퍼 ====================

    private String customerToken(Long memberId) {
        return jwtProvider.generateAccessToken(memberId, "CUSTOMER");
    }

    private String adminToken() {
        return jwtProvider.generateAccessToken(0L, "ADMIN");
    }

    private Long createCategory(String name) {
        return client.post()
                .uri("/api/v1/admin/categories")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name))
                .retrieve()
                .toEntity(CategoryResponse.class)
                .getBody().id();
    }

    private Long createProduct(String name, Long categoryId) {
        return client.post()
                .uri("/api/v1/admin/products")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "categoryId", categoryId))
                .retrieve()
                .toEntity(ProductResponse.class)
                .getBody().id();
    }

    private Long createVariant(Long productId, long price, int stock) {
        return client.post()
                .uri("/api/v1/admin/products/" + productId + "/variants")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("price", price, "stock", stock))
                .retrieve()
                .toEntity(VariantResponse.class)
                .getBody().id();
    }

    private Long createOrder(Long memberId, List<Map<String, Object>> items) {
        return client.post()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("items", items))
                .retrieve()
                .toEntity(OrderResponse.class)
                .getBody().id();
    }

    private void setOrderStatusToPaymentInProgress(Long orderId) {
        jdbcTemplate.update("UPDATE orders SET status = 'PAYMENT_IN_PROGRESS' WHERE id = ?", orderId);
    }

    private void setRetryCount(Long orderId, int count) {
        jdbcTemplate.update("UPDATE orders SET retry_count = ? WHERE id = ?", count, orderId);
    }

    // ==================== 스케줄러 테스트 ====================

    @Test
    @DisplayName("processRetry가 PAYMENT_IN_PROGRESS 주문의 confirmPayment를 성공시키면 PAID로 전환된다")
    void processRetrySuccessChangesToPaid() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 2)));

        setOrderStatusToPaymentInProgress(orderId);

        Order order = orderRepository.findById(orderId).orElseThrow();
        paymentRetryScheduler.processRetry(order);

        Order result = orderRepository.findById(orderId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);

        var variant = productVariantRepository.findById(variantId).orElseThrow();
        assertThat(variant.getAvailableStock()).isEqualTo(3);
    }

    @Test
    @DisplayName("processRetry 실패 시 retry_count가 1 증가한다")
    void processRetryFailureIncrementsRetryCount() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 2)));

        setOrderStatusToPaymentInProgress(orderId);
        // reserved_stock = 0 으로 강제 설정 → confirm() 실패
        jdbcTemplate.update("UPDATE product_variants SET reserved_stock = 0 WHERE id = ?", variantId);

        Order order = orderRepository.findById(orderId).orElseThrow();
        paymentRetryScheduler.processRetry(order);

        Order result = orderRepository.findById(orderId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getLastRetryAt()).isNotNull();
    }

    @Test
    @DisplayName("retry_count가 10에 도달하면 PAYMENT_FAILED로 전환되고 alertOps가 호출된다")
    void processRetryExhaustionTransitionsToPaymentFailed() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 2)));

        setOrderStatusToPaymentInProgress(orderId);
        setRetryCount(orderId, 9);
        // reserved_stock = 0 → confirm() 실패 → retry_count = 10 → PAYMENT_FAILED
        jdbcTemplate.update("UPDATE product_variants SET reserved_stock = 0 WHERE id = ?", variantId);

        Order order = orderRepository.findById(orderId).orElseThrow();
        paymentRetryScheduler.processRetry(order);

        Order result = orderRepository.findById(orderId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(fakeOpsAlertService.getAlertedOrderIds()).contains(orderId);
    }

    @Test
    @DisplayName("retryPayments가 threshold 이전 PAYMENT_IN_PROGRESS 주문만 조회한다")
    void retryPaymentsOnlyPicksUpOrdersBeforeThreshold() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long oldOrderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));
        Long freshOrderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        setOrderStatusToPaymentInProgress(oldOrderId);
        setOrderStatusToPaymentInProgress(freshOrderId);

        // oldOrder의 updated_at을 10분 전으로 설정
        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);
        jdbcTemplate.update("UPDATE orders SET updated_at = ? WHERE id = ?", tenMinutesAgo, oldOrderId);

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        var retryable = orderRepository.findRetryablePaymentInProgressOrders(threshold);

        assertThat(retryable).extracting(Order::getId).contains(oldOrderId);
        assertThat(retryable).extracting(Order::getId).doesNotContain(freshOrderId);
    }
}