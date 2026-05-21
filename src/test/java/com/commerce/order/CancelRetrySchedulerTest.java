package com.commerce.order;

import com.commerce.global.jwt.JwtProvider;
import com.commerce.order.domain.Order;
import com.commerce.order.domain.OrderStatus;
import com.commerce.order.dto.OrderResponse;
import com.commerce.order.repository.OrderItemRepository;
import com.commerce.order.repository.OrderRepository;
import com.commerce.order.scheduler.CancelRetryScheduler;
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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CancelRetrySchedulerTest {

    @LocalServerPort
    private int port;

    private RestClient client;

    @Autowired private JwtProvider jwtProvider;
    @Autowired private CancelRetryScheduler cancelRetryScheduler;
    @Autowired private FakePaymentGateway fakePaymentGateway;
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

    // 환불 실패로 CANCEL_IN_PROGRESS 상태를 만드는 헬퍼
    private Long createCancelInProgressOrder(Long memberId, Long variantId, int quantity) {
        Long orderId = createOrder(memberId, List.of(Map.of("variantId", variantId, "quantity", quantity)));

        client.post()
                .uri("/api/v1/orders/" + orderId + "/pay")
                .header("Authorization", "Bearer " + customerToken(memberId))
                .retrieve()
                .toBodilessEntity();

        fakePaymentGateway.setRefundWillFail(true);
        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken(memberId))
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpServerErrorException.class);
        fakePaymentGateway.setRefundWillFail(false);
        fakePaymentGateway.reset();

        return orderId;
    }

    // ==================== 스케줄러 테스트 ====================

    @Test
    @DisplayName("processRetry가 CANCEL_IN_PROGRESS 주문의 환불을 성공시키면 CANCELLED로 전환되고 재고가 복구된다")
    void processRetry_succeeds_transitions_to_cancelled_and_restores_stock() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5);
        Long orderId = createCancelInProgressOrder(1L, variantId, 2);

        Order order = orderRepository.findByIdWithItems(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_IN_PROGRESS);

        cancelRetryScheduler.processRetry(order);

        Order result = orderRepository.findById(orderId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(productVariantRepository.findById(variantId).orElseThrow().getAvailableStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("processRetry 환불 실패 시 CANCEL_IN_PROGRESS 상태를 유지한다")
    void processRetry_refund_failure_keeps_cancel_in_progress() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5);
        Long orderId = createCancelInProgressOrder(1L, variantId, 2);

        fakePaymentGateway.setRefundWillFail(true);
        Order order = orderRepository.findByIdWithItems(orderId).orElseThrow();
        cancelRetryScheduler.processRetry(order);

        Order result = orderRepository.findById(orderId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCEL_IN_PROGRESS);
        assertThat(productVariantRepository.findById(variantId).orElseThrow().getAvailableStock()).isEqualTo(3);
    }

    @Test
    @DisplayName("retryCancellations가 threshold 이전 CANCEL_IN_PROGRESS 주문만 조회한다")
    void retryCancellations_only_picks_up_orders_before_threshold() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);

        Long oldOrderId = createCancelInProgressOrder(1L, variantId, 1);
        Long freshOrderId = createCancelInProgressOrder(2L, variantId, 1);

        // oldOrder의 updated_at을 10분 전으로 설정
        jdbcTemplate.update("UPDATE orders SET updated_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(10), oldOrderId);

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        var retryable = orderRepository.findCancelInProgressOrdersBefore(OrderStatus.CANCEL_IN_PROGRESS, threshold);

        assertThat(retryable).extracting(Order::getId).contains(oldOrderId);
        assertThat(retryable).extracting(Order::getId).doesNotContain(freshOrderId);
    }
}