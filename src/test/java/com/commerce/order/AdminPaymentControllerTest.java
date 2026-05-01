package com.commerce.order;

import com.commerce.global.jwt.JwtProvider;
import com.commerce.order.domain.Order;
import com.commerce.order.domain.OrderStatus;
import com.commerce.order.domain.PaymentVerification;
import com.commerce.order.dto.OrderResponse;
import com.commerce.order.dto.VerifyPaymentResponse;
import com.commerce.order.repository.AuditLogRepository;
import com.commerce.order.repository.OrderItemRepository;
import com.commerce.order.repository.OrderRepository;
import com.commerce.order.repository.PaymentVerificationRepository;
import com.commerce.order.service.FakePaymentGateway;
import com.commerce.order.service.PgPaymentStatus;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminPaymentControllerTest {

    @LocalServerPort
    private int port;

    private RestClient client;

    @Autowired private JwtProvider jwtProvider;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentVerificationRepository paymentVerificationRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private FakePaymentGateway fakePaymentGateway;
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
        auditLogRepository.deleteAll();
        paymentVerificationRepository.deleteAll();
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

    private void setOrderStatus(Long orderId, String status) {
        jdbcTemplate.update("UPDATE orders SET status = ? WHERE id = ?", status, orderId);
    }

    private Long createOrderInPaymentFailedState(Long memberId, Long variantId, int quantity) {
        Long orderId = createOrder(memberId, List.of(Map.of("variantId", variantId, "quantity", quantity)));
        setOrderStatus(orderId, "PAYMENT_FAILED");
        return orderId;
    }

    // ==================== verify-payment ====================

    @Test
    @DisplayName("verify-payment API가 PG 상태를 조회하고 PaymentVerification을 저장한다")
    void verifyPaymentSavesPgStatusAndReturnsResult() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        fakePaymentGateway.setQueryResult(orderId, PgPaymentStatus.SUCCESS);

        var response = client.post()
                .uri("/api/v1/admin/orders/" + orderId + "/verify-payment")
                .header("Authorization", "Bearer " + adminToken())
                .retrieve()
                .toEntity(VerifyPaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().pgStatus()).isEqualTo(PgPaymentStatus.SUCCESS);
        assertThat(response.getBody().orderId()).isEqualTo(orderId);

        var saved = paymentVerificationRepository.findFirstByOrderIdOrderByVerifiedAtDesc(orderId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getPgStatus()).isEqualTo(PgPaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("verify-payment API가 존재하지 않는 주문에 대해 404를 반환한다")
    void verifyPaymentOfNonExistentOrderReturns404() {
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/orders/99999/verify-payment")
                        .header("Authorization", "Bearer " + adminToken())
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("ORDER_NOT_FOUND");
    }

    @Test
    @DisplayName("verify-payment API가 비인증 요청을 거부한다 (401)")
    void verifyPaymentWithoutAuthReturns401() {
        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/admin/orders/1/verify-payment")
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    @DisplayName("verify-payment API가 CUSTOMER 권한 요청을 거부한다 (403)")
    void verifyPaymentWithCustomerTokenReturns403() {
        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/admin/orders/1/verify-payment")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    // ==================== force-confirm ====================

    @Test
    @DisplayName("force-confirm API가 PAYMENT_FAILED 주문을 PAID로 전환하고 재고를 확정한다")
    void forceConfirmChangePaymentFailedToPaidAndConfirmsStock() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrderInPaymentFailedState(1L, variantId, 2);

        fakePaymentGateway.setQueryResult(orderId, PgPaymentStatus.SUCCESS);
        client.post()
                .uri("/api/v1/admin/orders/" + orderId + "/verify-payment")
                .header("Authorization", "Bearer " + adminToken())
                .retrieve().toBodilessEntity();

        var response = client.post()
                .uri("/api/v1/admin/orders/" + orderId + "/force-confirm")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", "PG사 확인 후 수동 처리"))
                .retrieve()
                .toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.PAID);

        var order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

        var auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getAction()).isEqualTo("FORCE_CONFIRM");

        var variant = productVariantRepository.findById(variantId).orElseThrow();
        assertThat(variant.getAvailableStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("force-confirm API가 reason 없이 요청 시 400을 반환한다")
    void forceConfirmWithoutReasonReturns400() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrderInPaymentFailedState(1L, variantId, 1);

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/orders/" + orderId + "/force-confirm")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", ""))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.BadRequest.class
        );

        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("force-confirm API가 PAYMENT_FAILED 아닌 상태에서 400을 반환한다")
    void forceConfirmOnNonPaymentFailedOrderReturns400() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/orders/" + orderId + "/force-confirm")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", "수동 처리"))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.BadRequest.class
        );

        assertThat(ex.getResponseBodyAsString()).contains("ORDER_INVALID_STATUS");
    }

    @Test
    @DisplayName("force-confirm API가 verify-payment 없이 요청 시 400을 반환한다")
    void forceConfirmWithoutVerifyReturns400() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrderInPaymentFailedState(1L, variantId, 1);

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/orders/" + orderId + "/force-confirm")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", "수동 처리"))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.BadRequest.class
        );

        assertThat(ex.getResponseBodyAsString()).contains("PAYMENT_VERIFY_REQUIRED");
    }

    @Test
    @DisplayName("force-confirm API가 pgStatus FAIL일 때 400을 반환한다")
    void forceConfirmWithPgStatusFailReturns400() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrderInPaymentFailedState(1L, variantId, 1);

        fakePaymentGateway.setQueryResult(orderId, PgPaymentStatus.FAIL);
        client.post()
                .uri("/api/v1/admin/orders/" + orderId + "/verify-payment")
                .header("Authorization", "Bearer " + adminToken())
                .retrieve().toBodilessEntity();

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/orders/" + orderId + "/force-confirm")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", "수동 처리"))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.BadRequest.class
        );

        assertThat(ex.getResponseBodyAsString()).contains("PAYMENT_VERIFY_MISMATCH");
    }

    @Test
    @DisplayName("force-confirm API가 멱등 재요청 시 AuditLog를 추가 생성하지 않고 성공 응답을 반환한다")
    void forceConfirmIdempotentSecondCallReturnsSuccessWithoutExtraAuditLog() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrderInPaymentFailedState(1L, variantId, 1);

        fakePaymentGateway.setQueryResult(orderId, PgPaymentStatus.SUCCESS);
        client.post()
                .uri("/api/v1/admin/orders/" + orderId + "/verify-payment")
                .header("Authorization", "Bearer " + adminToken())
                .retrieve().toBodilessEntity();

        client.post()
                .uri("/api/v1/admin/orders/" + orderId + "/force-confirm")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", "수동 처리"))
                .retrieve().toBodilessEntity();

        // 두 번째 호출
        var response = client.post()
                .uri("/api/v1/admin/orders/" + orderId + "/force-confirm")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", "수동 처리"))
                .retrieve()
                .toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.PAID);
        assertThat(auditLogRepository.findAll()).hasSize(1);
    }

    // ==================== force-cancel ====================

    @Test
    @DisplayName("force-cancel API가 PAYMENT_FAILED 주문을 CANCELLED로 전환하고 재고를 복구한다")
    void forceCancelChangesPaymentFailedToCancelledAndReleasesStock() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrderInPaymentFailedState(1L, variantId, 3);

        fakePaymentGateway.setQueryResult(orderId, PgPaymentStatus.FAIL);
        client.post()
                .uri("/api/v1/admin/orders/" + orderId + "/verify-payment")
                .header("Authorization", "Bearer " + adminToken())
                .retrieve().toBodilessEntity();

        var response = client.post()
                .uri("/api/v1/admin/orders/" + orderId + "/force-cancel")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", "PG사 결제 실패 확인"))
                .retrieve()
                .toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.CANCELLED);

        var order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        var auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getAction()).isEqualTo("FORCE_CANCEL");

        var variant = productVariantRepository.findById(variantId).orElseThrow();
        assertThat(variant.getAvailableStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("force-cancel API가 pgStatus SUCCESS일 때 400을 반환한다")
    void forceCancelWithPgStatusSuccessReturns400() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrderInPaymentFailedState(1L, variantId, 1);

        fakePaymentGateway.setQueryResult(orderId, PgPaymentStatus.SUCCESS);
        client.post()
                .uri("/api/v1/admin/orders/" + orderId + "/verify-payment")
                .header("Authorization", "Bearer " + adminToken())
                .retrieve().toBodilessEntity();

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/orders/" + orderId + "/force-cancel")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", "수동 취소"))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.BadRequest.class
        );

        assertThat(ex.getResponseBodyAsString()).contains("PAYMENT_VERIFY_MISMATCH");
    }

    @Test
    @DisplayName("force-cancel API가 verify-payment 없이 요청 시 400을 반환한다")
    void forceCancelWithoutVerifyReturns400() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrderInPaymentFailedState(1L, variantId, 1);

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/orders/" + orderId + "/force-cancel")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", "수동 취소"))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.BadRequest.class
        );

        assertThat(ex.getResponseBodyAsString()).contains("PAYMENT_VERIFY_REQUIRED");
    }

    @Test
    @DisplayName("force-cancel API가 비인증 요청을 거부한다 (401)")
    void forceCancelWithoutAuthReturns401() {
        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/admin/orders/1/force-cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", "수동 취소"))
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }
}