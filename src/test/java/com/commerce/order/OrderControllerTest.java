package com.commerce.order;

import com.commerce.global.jwt.JwtProvider;
import com.commerce.order.domain.OrderStatus;
import com.commerce.order.dto.OrderDetailResponse;
import com.commerce.order.dto.OrderResponse;
import com.commerce.order.repository.OrderItemRepository;
import com.commerce.order.repository.OrderRepository;
import com.commerce.product.domain.VariantStatus;
import com.commerce.product.dto.CategoryResponse;
import com.commerce.product.dto.OptionResponse;
import com.commerce.product.dto.PageResponse;
import com.commerce.product.dto.ProductResponse;
import com.commerce.product.dto.VariantResponse;
import com.commerce.product.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderControllerTest {

    @LocalServerPort
    private int port;

    private RestClient client;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductVariantOptionRepository productVariantOptionRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductOptionValueRepository productOptionValueRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productVariantOptionRepository.deleteAll();
        productVariantRepository.deleteAll();
        productOptionValueRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    // ==================== 헬퍼 메서드 ====================

    private String customerToken(Long memberId) {
        return jwtProvider.generateAccessToken(memberId, "CUSTOMER");
    }

    private String adminToken() {
        return jwtProvider.generateAccessToken(0L, "ADMIN");
    }

    private Long createCategory(String name) {
        var response = client.post()
                .uri("/api/v1/admin/categories")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name))
                .retrieve()
                .toEntity(CategoryResponse.class);
        return response.getBody().id();
    }

    private Long createProduct(String name, Long categoryId) {
        var response = client.post()
                .uri("/api/v1/admin/products")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "categoryId", categoryId))
                .retrieve()
                .toEntity(ProductResponse.class);
        return response.getBody().id();
    }

    private Long createVariant(Long productId, long price, int stock) {
        var response = client.post()
                .uri("/api/v1/admin/products/" + productId + "/variants")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("price", price, "stock", stock))
                .retrieve()
                .toEntity(VariantResponse.class);
        return response.getBody().id();
    }

    private Long createVariantWithStatus(Long productId, long price, int stock, String status) {
        Long variantId = createVariant(productId, price, stock);
        if ("STOPPED".equals(status)) {
            client.put()
                    .uri("/api/v1/admin/variants/" + variantId)
                    .header("Authorization", "Bearer " + adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("price", price, "status", "STOPPED"))
                    .retrieve()
                    .toBodilessEntity();
        }
        return variantId;
    }

    private Long createOrder(Long memberId, List<Map<String, Object>> items) {
        var response = client.post()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("items", items))
                .retrieve()
                .toEntity(OrderResponse.class);
        return response.getBody().id();
    }

    // ==================== 주문 생성 API ====================

    @Test
    @DisplayName("주문 생성 API가 단일 Variant 주문을 성공적으로 생성한다")
    void createOrderWithSingleVariant() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);

        var body = Map.of("items", List.of(
                Map.of("variantId", variantId, "quantity", 2)
        ));

        var response = client.post()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getBody().totalAmount()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("주문 생성 API가 다중 Variant 주문을 하나의 트랜잭션으로 생성한다")
    void createOrderWithMultipleVariants() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId1 = createVariant(productId, 10000L, 10);
        Long variantId2 = createVariant(productId, 20000L, 5);

        var body = Map.of("items", List.of(
                Map.of("variantId", variantId1, "quantity", 2),
                Map.of("variantId", variantId2, "quantity", 1)
        ));

        var response = client.post()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().totalAmount()).isEqualTo(40000L);
    }

    @Test
    @DisplayName("주문 생성 API가 초기 상태를 PENDING으로 설정한다")
    void createOrderInitialStatusIsPending() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);

        var body = Map.of("items", List.of(
                Map.of("variantId", variantId, "quantity", 1)
        ));

        var response = client.post()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(OrderResponse.class);

        assertThat(response.getBody().status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("주문 생성 API가 total_amount를 Σ(price × quantity)로 계산한다")
    void createOrderCalculatesTotalAmount() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId1 = createVariant(productId, 5000L, 10);
        Long variantId2 = createVariant(productId, 3000L, 10);

        var body = Map.of("items", List.of(
                Map.of("variantId", variantId1, "quantity", 3),
                Map.of("variantId", variantId2, "quantity", 4)
        ));

        var response = client.post()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(OrderResponse.class);

        // 5000*3 + 3000*4 = 15000 + 12000 = 27000
        assertThat(response.getBody().totalAmount()).isEqualTo(27000L);
    }

    @Test
    @DisplayName("주문 생성 API가 주문 시점의 price와 product_name을 OrderItem에 스냅샷으로 저장한다")
    void createOrderSnapshotsPriceAndProductName() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);

        var body = Map.of("items", List.of(
                Map.of("variantId", variantId, "quantity", 1)
        ));

        var createResponse = client.post()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(OrderResponse.class);

        Long orderId = createResponse.getBody().id();

        var detailResponse = client.get()
                .uri("/api/v1/orders/" + orderId)
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toEntity(OrderDetailResponse.class);

        assertThat(detailResponse.getBody().items()).hasSize(1);
        assertThat(detailResponse.getBody().items().get(0).productName()).isEqualTo("티셔츠");
        assertThat(detailResponse.getBody().items().get(0).price()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("주문 생성 API가 재고를 차감한다")
    void createOrderDecreasesStock() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);

        var body = Map.of("items", List.of(
                Map.of("variantId", variantId, "quantity", 3)
        ));

        client.post()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        // 재고가 7로 줄었는지 확인 (이후 추가 주문 가능 여부로 간접 확인)
        var body2 = Map.of("items", List.of(
                Map.of("variantId", variantId, "quantity", 7)
        ));

        var response = client.post()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(2L))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body2)
                .retrieve()
                .toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("주문 생성 API가 SOLD_OUT Variant 주문 요청을 거부한다 (400, VARIANT_SOLD_OUT)")
    void createOrderWithSoldOutVariantReturns400() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        // stock=0 이면 자동으로 SOLD_OUT
        Long variantId = createVariant(productId, 10000L, 0);

        var body = Map.of("items", List.of(
                Map.of("variantId", variantId, "quantity", 1)
        ));

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.BadRequest.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("VARIANT_SOLD_OUT");
    }

    @Test
    @DisplayName("주문 생성 API가 STOPPED Variant 주문 요청을 거부한다 (400, VARIANT_SOLD_OUT)")
    void createOrderWithStoppedVariantReturns400() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariantWithStatus(productId, 10000L, 10, "STOPPED");

        var body = Map.of("items", List.of(
                Map.of("variantId", variantId, "quantity", 1)
        ));

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.BadRequest.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("VARIANT_SOLD_OUT");
    }

    @Test
    @DisplayName("주문 생성 API가 존재하지 않는 Variant 요청을 거부한다 (404, VARIANT_NOT_FOUND)")
    void createOrderWithNonExistentVariantReturns404() {
        var body = Map.of("items", List.of(
                Map.of("variantId", 99999L, "quantity", 1)
        ));

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("VARIANT_NOT_FOUND");
    }

    @Test
    @DisplayName("주문 생성 API가 재고 부족 시 주문 생성을 실패한다 (400, OUT_OF_STOCK)")
    void createOrderWithInsufficientStockReturns400() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5);

        var body = Map.of("items", List.of(
                Map.of("variantId", variantId, "quantity", 10)
        ));

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.BadRequest.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("OUT_OF_STOCK");
    }

    @Test
    @DisplayName("주문 생성 API가 재고 부족 아이템이 포함된 다중 주문 전체를 롤백한다")
    void createOrderRollsBackAllItemsWhenOneOutOfStock() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId1 = createVariant(productId, 10000L, 10);
        Long variantId2 = createVariant(productId, 20000L, 2); // stock=2, 5개 요청하면 실패

        var body = Map.of("items", List.of(
                Map.of("variantId", variantId1, "quantity", 2),
                Map.of("variantId", variantId2, "quantity", 5)
        ));

        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.BadRequest.class);

        // 주문이 생성되지 않았는지 확인
        var ordersResponse = client.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<OrderResponse>>() {});

        assertThat(ordersResponse.getBody().content()).isEmpty();
    }

    @Test
    @DisplayName("주문 생성 API가 비인증 요청을 거부한다 (401)")
    void createOrderWithoutAuthReturns401() {
        var body = Map.of("items", List.of(
                Map.of("variantId", 1L, "quantity", 1)
        ));

        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    // ==================== 결제 API ====================

    @Test
    @DisplayName("결제 API가 PENDING 주문을 PAID로 전이한다")
    void payOrderChangesPendingToPaid() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        var response = client.post()
                .uri("/api/v1/orders/" + orderId + "/pay")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("결제 API가 PAID 주문 재결제 요청을 거부한다 (400, ORDER_INVALID_STATUS)")
    void payAlreadyPaidOrderReturns400() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        // 첫 번째 결제
        client.post()
                .uri("/api/v1/orders/" + orderId + "/pay")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        // 두 번째 결제 시도
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.BadRequest.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("ORDER_INVALID_STATUS");
    }

    @Test
    @DisplayName("결제 API가 CANCELLED 주문 결제 요청을 거부한다 (400, ORDER_INVALID_STATUS)")
    void payCancelledOrderReturns400() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        // 취소
        client.post()
                .uri("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        // 결제 시도
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.BadRequest.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("ORDER_INVALID_STATUS");
    }

    @Test
    @DisplayName("결제 API가 타인 주문 접근을 거부한다 (403, ORDER_ACCESS_DENIED)")
    void payOtherMemberOrderReturns403() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + customerToken(2L))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Forbidden.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("ORDER_ACCESS_DENIED");
    }

    @Test
    @DisplayName("결제 API가 존재하지 않는 주문 요청을 거부한다 (404, ORDER_NOT_FOUND)")
    void payNonExistentOrderReturns404() {
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/orders/99999/pay")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("ORDER_NOT_FOUND");
    }

    @Test
    @DisplayName("결제 API가 비인증 요청을 거부한다 (401)")
    void payOrderWithoutAuthReturns401() {
        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/orders/1/pay")
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    // ==================== 취소 API ====================

    @Test
    @DisplayName("취소 API가 PENDING 주문을 CANCELLED로 전이한다")
    void cancelOrderChangesPendingToCancelled() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        var response = client.post()
                .uri("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("취소 API가 주문 취소 시 OrderItem 수량만큼 재고를 복구한다")
    void cancelOrderRestoresStock() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 3)));

        // 취소 후 재고가 복구되어 다시 10개 주문 가능해야 함
        client.post()
                .uri("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        // 재고 복구 확인: 7+3=10 → 10개 주문 가능
        var body = Map.of("items", List.of(
                Map.of("variantId", variantId, "quantity", 10)
        ));

        var response = client.post()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(2L))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("취소 API가 PAID 주문 취소 요청을 거부한다 (400, ORDER_INVALID_STATUS)")
    void cancelPaidOrderReturns400() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        // 결제
        client.post()
                .uri("/api/v1/orders/" + orderId + "/pay")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        // 취소 시도
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.BadRequest.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("ORDER_INVALID_STATUS");
    }

    @Test
    @DisplayName("취소 API가 CANCELLED 주문 재취소 요청을 거부한다 (400, ORDER_INVALID_STATUS)")
    void cancelAlreadyCancelledOrderReturns400() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        // 첫 번째 취소
        client.post()
                .uri("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        // 두 번째 취소 시도
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.BadRequest.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("ORDER_INVALID_STATUS");
    }

    @Test
    @DisplayName("취소 API가 타인 주문 접근을 거부한다 (403, ORDER_ACCESS_DENIED)")
    void cancelOtherMemberOrderReturns403() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken(2L))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Forbidden.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("ORDER_ACCESS_DENIED");
    }

    @Test
    @DisplayName("취소 API가 존재하지 않는 주문 요청을 거부한다 (404, ORDER_NOT_FOUND)")
    void cancelNonExistentOrderReturns404() {
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/orders/99999/cancel")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("ORDER_NOT_FOUND");
    }

    @Test
    @DisplayName("취소 API가 비인증 요청을 거부한다 (401)")
    void cancelOrderWithoutAuthReturns401() {
        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/orders/1/cancel")
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    // ==================== 주문 목록 조회 API ====================

    @Test
    @DisplayName("주문 목록 조회 API가 본인 주문만 반환한다")
    void getMyOrdersReturnsOnlyOwnOrders() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);

        // member 1이 주문 2개 생성
        createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));
        createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));
        // member 2가 주문 1개 생성
        createOrder(2L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        var response = client.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<OrderResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).hasSize(2);
        assertThat(response.getBody().content()).allMatch(o -> o.memberId().equals(1L));
    }

    @Test
    @DisplayName("주문 목록 조회 API가 created_at DESC 정렬을 적용한다")
    void getMyOrdersReturnsSortedByCreatedAtDesc() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);

        Long orderId1 = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));
        Long orderId2 = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        var response = client.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<OrderResponse>>() {});

        var orders = response.getBody().content();
        assertThat(orders).hasSize(2);
        // 최신 주문(orderId2)이 먼저 와야 함
        assertThat(orders.get(0).id()).isGreaterThan(orders.get(1).id());
    }

    @Test
    @DisplayName("주문 목록 조회 API가 page/size 기반 pagination을 적용한다")
    void getMyOrdersAppliesPagination() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 20);

        // 5개 주문 생성
        for (int i = 0; i < 5; i++) {
            createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));
        }

        var response = client.get()
                .uri("/api/v1/orders?page=0&size=2")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<OrderResponse>>() {});

        assertThat(response.getBody().content()).hasSize(2);
        assertThat(response.getBody().totalElements()).isEqualTo(5);
        assertThat(response.getBody().totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("주문 목록 조회 API가 비인증 요청을 거부한다 (401)")
    void getMyOrdersWithoutAuthReturns401() {
        assertThatThrownBy(() ->
                client.get()
                        .uri("/api/v1/orders")
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    // ==================== 주문 상세 조회 API ====================

    @Test
    @DisplayName("주문 상세 조회 API가 Order와 OrderItem 목록을 반환한다")
    void getOrderDetailReturnsOrderWithItems() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId1 = createVariant(productId, 10000L, 10);
        Long variantId2 = createVariant(productId, 20000L, 10);

        Long orderId = createOrder(1L, List.of(
                Map.of("variantId", variantId1, "quantity", 2),
                Map.of("variantId", variantId2, "quantity", 1)
        ));

        var response = client.get()
                .uri("/api/v1/orders/" + orderId)
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toEntity(OrderDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(orderId);
        assertThat(response.getBody().items()).hasSize(2);
        assertThat(response.getBody().totalAmount()).isEqualTo(40000L);
    }

    @Test
    @DisplayName("주문 상세 조회 API가 타인 주문 접근을 거부한다 (403, ORDER_ACCESS_DENIED)")
    void getOrderDetailOfOtherMemberReturns403() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        var ex = catchThrowableOfType(
                () -> client.get()
                        .uri("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken(2L))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Forbidden.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("ORDER_ACCESS_DENIED");
    }

    @Test
    @DisplayName("주문 상세 조회 API가 존재하지 않는 주문 요청을 거부한다 (404, ORDER_NOT_FOUND)")
    void getOrderDetailOfNonExistentOrderReturns404() {
        var ex = catchThrowableOfType(
                () -> client.get()
                        .uri("/api/v1/orders/99999")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("ORDER_NOT_FOUND");
    }

    @Test
    @DisplayName("주문 상세 조회 API가 비인증 요청을 거부한다 (401)")
    void getOrderDetailWithoutAuthReturns401() {
        assertThatThrownBy(() ->
                client.get()
                        .uri("/api/v1/orders/1")
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    // ==================== 추가 시나리오 ====================

    @Test
    @DisplayName("주문 생성 API가 quantity 0 요청을 거부한다")
    void order_creation_rejects_quantity_zero() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);

        var body = Map.of("items", List.of(
                Map.of("variantId", variantId, "quantity", 0)
        ));

        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    @DisplayName("주문 생성 API가 동일 Variant를 중복 포함한 주문을 각각 독립 차감으로 처리한다")
    void order_creation_with_duplicate_variant_processes_independently() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);

        // 동일 variantId를 두 번 포함한 주문 (각 quantity=3) → 총 6 차감
        var body = Map.of("items", List.of(
                Map.of("variantId", variantId, "quantity", 3),
                Map.of("variantId", variantId, "quantity", 3)
        ));

        var response = client.post()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        int finalStock = productVariantRepository.findById(variantId).get().getStock();
        assertThat(finalStock).isEqualTo(4);
    }

    @Test
    @DisplayName("주문 생성 API가 상품 가격 변경 후에도 OrderItem의 price를 주문 시점 값으로 유지한다")
    void order_item_price_snapshot_immutable_after_price_change() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);

        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        // 가격 변경
        client.put()
                .uri("/api/v1/admin/variants/" + variantId)
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("price", 99999L, "status", "ON_SALE"))
                .retrieve()
                .toBodilessEntity();

        // 주문 상세 조회 → 스냅샷 가격 확인
        var detailResponse = client.get()
                .uri("/api/v1/orders/" + orderId)
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toEntity(OrderDetailResponse.class);

        assertThat(detailResponse.getBody().items().get(0).price()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("취소 API가 재고 복구 시 SOLD_OUT 상태의 Variant를 ON_SALE로 전환한다")
    void cancel_restores_stock_and_transitions_sold_out_to_on_sale() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 1);

        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        // 주문 취소
        client.post()
                .uri("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        // 재고 복구 후 Variant 상태가 ON_SALE로 전환됐는지 확인
        VariantStatus finalStatus = productVariantRepository.findById(variantId).get().getStatus();
        assertThat(finalStatus).isEqualTo(VariantStatus.ON_SALE);
    }

    @Test
    @DisplayName("주문 목록 조회 API가 다른 사용자의 주문을 결과에 포함하지 않는다")
    void order_list_excludes_other_users_orders() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);

        // 사용자 A 주문
        createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));
        // 사용자 B 주문
        createOrder(2L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        // 사용자 A 조회 → 자신의 주문 1건만
        var responseA = client.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<OrderResponse>>() {});

        assertThat(responseA.getBody().totalElements()).isEqualTo(1);
        assertThat(responseA.getBody().content()).allMatch(o -> o.memberId().equals(1L));

        // 사용자 B 조회 → 자신의 주문 1건만
        var responseB = client.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(2L))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<OrderResponse>>() {});

        assertThat(responseB.getBody().totalElements()).isEqualTo(1);
        assertThat(responseB.getBody().content()).allMatch(o -> o.memberId().equals(2L));
    }
}