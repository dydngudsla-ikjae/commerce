package com.commerce.order;

import com.commerce.global.jwt.JwtProvider;
import com.commerce.order.domain.OrderStatus;
import com.commerce.order.dto.OrderDetailResponse;
import com.commerce.order.dto.OrderResponse;
import com.commerce.order.repository.OrderItemRepository;
import com.commerce.order.repository.OrderRepository;
import com.commerce.order.service.FakePaymentGateway;
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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Autowired
    private FakePaymentGateway fakePaymentGateway;

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
        assertThat(productVariantRepository.findById(variantId).get().getAvailableStock()).isEqualTo(4);
    }

    @Test
    @DisplayName("주문 생성 API가 주문으로 재고가 0이 되면 Variant 상태를 SOLD_OUT으로 전환한다")
    void createOrder_transitions_variant_to_sold_out_when_stock_reaches_zero() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 2);

        createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 2)));

        VariantStatus status = productVariantRepository.findById(variantId).get().getStatus();
        assertThat(status).isEqualTo(VariantStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("주문 생성 API가 동시 요청에서 재고를 초과하지 않는다 (낙관적 락)")
    void createOrder_does_not_oversell_under_concurrent_requests() throws InterruptedException {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 1);

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    var body = Map.of("items", List.of(Map.of("variantId", variantId, "quantity", 1)));
                    client.post()
                            .uri("/api/v1/orders")
                            .header("Authorization", "Bearer " + customerToken(memberId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .toBodilessEntity();
                    successCount.incrementAndGet();
                } catch (HttpClientErrorException e) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(productVariantRepository.findById(variantId).get().getAvailableStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("주문 생성 API가 상품 가격 변경 후에도 OrderItem의 price를 주문 시점 값으로 유지한다")
    void order_item_price_snapshot_immutable_after_price_change() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);

        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        client.put()
                .uri("/api/v1/admin/variants/" + variantId)
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("price", 99999L, "status", "ON_SALE"))
                .retrieve()
                .toBodilessEntity();

        var detailResponse = client.get()
                .uri("/api/v1/orders/" + orderId)
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toEntity(OrderDetailResponse.class);

        assertThat(detailResponse.getBody().items().get(0).price()).isEqualTo(10000L);
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

    @Test
    @DisplayName("결제 API가 pg.charge 실패 시 주문을 CANCELLED로 전환하고 재고를 복구한다 (502)")
    void payOrderWithPgChargeFailureCancelsOrderAndRestoresStock() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 2)));

        fakePaymentGateway.setChargeWillFail(true);

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .retrieve()
                        .toBodilessEntity(),
                HttpServerErrorException.class
        );

        assertThat(ex.getStatusCode().value()).isEqualTo(502);
        assertThat(ex.getResponseBodyAsString()).contains("PAYMENT_GATEWAY_ERROR");

        var order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        var variant = productVariantRepository.findById(variantId).orElseThrow();
        assertThat(variant.getAvailableStock()).isEqualTo(5);
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
    @DisplayName("취소 API가 동시 요청에서 재고를 중복 복구하지 않는다 (낙관적 락)")
    void cancel_does_not_duplicate_stock_restore_under_concurrent_requests() throws InterruptedException {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 1);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    client.post()
                            .uri("/api/v1/orders/" + orderId + "/cancel")
                            .header("Authorization", "Bearer " + customerToken(1L))
                            .retrieve()
                            .toBodilessEntity();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패 (ORDER_INVALID_STATUS 또는 낙관적 락 충돌)
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(productVariantRepository.findById(variantId).get().getAvailableStock()).isEqualTo(1);
    }

    @Test
    @DisplayName("취소 API가 재고 복구 시 SOLD_OUT 상태의 Variant를 ON_SALE로 전환한다")
    void cancel_restores_stock_and_transitions_sold_out_to_on_sale() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 1);

        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        client.post()
                .uri("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        VariantStatus finalStatus = productVariantRepository.findById(variantId).get().getStatus();
        assertThat(finalStatus).isEqualTo(VariantStatus.ON_SALE);
    }

    @Test
    @DisplayName("취소 API가 PAID 주문을 CANCELLED로 전이한다")
    void cancelPaidOrderChangesPaidToCancelled() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        client.post()
                .uri("/api/v1/orders/" + orderId + "/pay")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        var response = client.post()
                .uri("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("취소 API가 PAID 주문 취소 시 OrderItem 수량만큼 재고를 복구한다")
    void cancelPaidOrderRestoresStock() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 3)));

        client.post()
                .uri("/api/v1/orders/" + orderId + "/pay")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        client.post()
                .uri("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        assertThat(productVariantRepository.findById(variantId).get().getAvailableStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("취소 API가 PAID 주문 취소 시 SOLD_OUT 상태의 Variant를 ON_SALE로 전환한다")
    void cancelPaidOrderTransitionsSoldOutVariantToOnSale() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 1);

        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        client.post()
                .uri("/api/v1/orders/" + orderId + "/pay")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        client.post()
                .uri("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        assertThat(productVariantRepository.findById(variantId).get().getStatus())
                .isEqualTo(VariantStatus.ON_SALE);
    }

    @Test
    @DisplayName("취소 API가 PAID 주문 취소 시 STOPPED 상태 Variant 재고를 복구해도 ON_SALE로 전환하지 않는다")
    void cancelPaidOrderDoesNotTransitionStoppedVariantToOnSale() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);

        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 3)));

        client.post()
                .uri("/api/v1/orders/" + orderId + "/pay")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        // Variant를 STOPPED로 전환
        client.put()
                .uri("/api/v1/admin/variants/" + variantId)
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("price", 10000L, "status", "STOPPED"))
                .retrieve()
                .toBodilessEntity();

        client.post()
                .uri("/api/v1/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        assertThat(productVariantRepository.findById(variantId).get().getStatus())
                .isEqualTo(VariantStatus.STOPPED);
    }

    @Test
    @DisplayName("취소 API가 PAID 주문 취소 시 PG 환불 실패하면 CANCEL_IN_PROGRESS를 유지한다 (502, REFUND_GATEWAY_ERROR)")
    void cancelPaidOrderWithRefundFailureKeepsOrderStatus() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        client.post()
                .uri("/api/v1/orders/" + orderId + "/pay")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        fakePaymentGateway.setRefundWillFail(true);

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .retrieve()
                        .toBodilessEntity(),
                HttpServerErrorException.class
        );

        assertThat(ex.getStatusCode().value()).isEqualTo(502);
        assertThat(ex.getResponseBodyAsString()).contains("REFUND_GATEWAY_ERROR");

        // 환불 실패 → CANCEL_IN_PROGRESS 유지 (CancelRetryScheduler가 재시도)
        assertThat(orderRepository.findById(orderId).get().getStatus()).isEqualTo(OrderStatus.CANCEL_IN_PROGRESS);
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

    @Test
    @DisplayName("주문 생성 API가 동시 주문에서 성공 건수만큼만 재고를 정확히 차감한다")
    void createOrder_deducts_stock_exactly_matching_success_count_under_concurrent_requests() throws InterruptedException {
        int initialStock = 5;
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, initialStock);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    var body = Map.of("items", List.of(Map.of("variantId", variantId, "quantity", 1)));
                    client.post()
                            .uri("/api/v1/orders")
                            .header("Authorization", "Bearer " + customerToken(memberId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .toBodilessEntity();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 성공/실패 무관 — 재고 정합성만 검증
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        int finalStock = productVariantRepository.findById(variantId).get().getAvailableStock();
        // 최소 1건은 성공해야 동시성 로직이 실제로 동작했음을 보장
        assertThat(successCount.get()).isGreaterThan(0);
        // 성공 건수 + 잔여 재고 = 초기 재고 (과차감 없음, 누락 차감 없음)
        assertThat(finalStock).isEqualTo(initialStock - successCount.get());
        assertThat(finalStock).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("취소 API가 PAID 주문 동시 취소 요청에서 pg.refund()를 정확히 1회 호출하고 재고를 1회 복구한다")
    void cancelPaid_restores_stock_exactly_once_under_concurrent_requests() throws InterruptedException {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 1);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        client.post()
                .uri("/api/v1/orders/" + orderId + "/pay")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    client.post()
                            .uri("/api/v1/orders/" + orderId + "/cancel")
                            .header("Authorization", "Bearer " + customerToken(1L))
                            .retrieve()
                            .toBodilessEntity();
                } catch (Exception e) {
                    // 오류(4xx/5xx) 발생해도 무시
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(orderRepository.findById(orderId).get().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(productVariantRepository.findById(variantId).get().getAvailableStock()).isEqualTo(1);
        assertThat(fakePaymentGateway.getRefundCallCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("취소 API가 CANCEL_IN_PROGRESS 주문에 재취소 요청 시 거부한다 (400, ORDER_INVALID_STATUS)")
    void cancel_returns_400_when_order_is_cancel_in_progress() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 1);
        Long orderId = createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        client.post()
                .uri("/api/v1/orders/" + orderId + "/pay")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toBodilessEntity();

        // 환불 실패 → CANCEL_IN_PROGRESS
        fakePaymentGateway.setRefundWillFail(true);
        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken(1L))
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpServerErrorException.class);

        // CANCEL_IN_PROGRESS 상태에서 재취소 → 400
        fakePaymentGateway.setRefundWillFail(false);
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
    @DisplayName("주문 목록 조회 API가 다른 사용자의 주문을 결과에 포함하지 않는다")
    void order_list_excludes_other_users_orders() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10);

        createOrder(1L, List.of(Map.of("variantId", variantId, "quantity", 1)));
        createOrder(2L, List.of(Map.of("variantId", variantId, "quantity", 1)));

        var responseA = client.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(1L))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<OrderResponse>>() {});

        assertThat(responseA.getBody().totalElements()).isEqualTo(1);
        assertThat(responseA.getBody().content()).allMatch(o -> o.memberId().equals(1L));

        var responseB = client.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + customerToken(2L))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<OrderResponse>>() {});

        assertThat(responseB.getBody().totalElements()).isEqualTo(1);
        assertThat(responseB.getBody().content()).allMatch(o -> o.memberId().equals(2L));
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

}