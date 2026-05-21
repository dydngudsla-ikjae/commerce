package com.commerce.product;

import com.commerce.global.jwt.JwtProvider;
import com.commerce.product.domain.ProductStatus;
import com.commerce.product.domain.VariantStatus;
import com.commerce.product.dto.*;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProductControllerTest {

    @LocalServerPort
    private int port;

    private RestClient client;

    @Autowired
    private JwtProvider jwtProvider;

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
        // 연관관계 순서대로 삭제
        productVariantOptionRepository.deleteAll();
        productVariantRepository.deleteAll();
        productOptionValueRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    // ==================== 헬퍼 메서드 ====================

    private String createAdminToken() {
        return jwtProvider.generateAccessToken(0L, "ADMIN");
    }

    private String createCustomerToken() {
        return jwtProvider.generateAccessToken(0L, "CUSTOMER");
    }

    private Long createCategory(String name) {
        var response = client.post()
                .uri("/api/v1/admin/categories")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name))
                .retrieve()
                .toEntity(CategoryResponse.class);
        return response.getBody().id();
    }

    private Long createCategoryWithParent(String name, Long parentId) {
        var response = client.post()
                .uri("/api/v1/admin/categories")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "parentId", parentId))
                .retrieve()
                .toEntity(CategoryResponse.class);
        return response.getBody().id();
    }

    private Long createProduct(String name, Long categoryId) {
        var response = client.post()
                .uri("/api/v1/admin/products")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "categoryId", categoryId))
                .retrieve()
                .toEntity(ProductResponse.class);
        return response.getBody().id();
    }

    private Long addOption(Long productId, String name, List<String> values) {
        var response = client.post()
                .uri("/api/v1/admin/products/" + productId + "/options")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "values", values))
                .retrieve()
                .toEntity(OptionResponse.class);
        return response.getBody().id();
    }

    private Long createVariant(Long productId, long price, int stock, List<Long> optionValueIds) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("price", price);
        body.put("stock", stock);
        if (optionValueIds != null && !optionValueIds.isEmpty()) {
            body.put("optionValueIds", optionValueIds);
        }
        var response = client.post()
                .uri("/api/v1/admin/products/" + productId + "/variants")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(VariantResponse.class);
        return response.getBody().id();
    }

    // ==================== 카테고리 생성 ====================

    @Test
    @DisplayName("카테고리 생성 API가 유효한 이름으로 카테고리를 등록한다")
    void createCategoryWithValidName() {
        var body = Map.of("name", "의류");

        var response = client.post()
                .uri("/api/v1/admin/categories")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(CategoryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("의류");
        assertThat(response.getBody().children()).isEmpty();
    }

    @Test
    @DisplayName("카테고리 생성 API가 parent_id로 하위 카테고리를 생성한다")
    void createChildCategoryWithParentId() {
        Long parentId = createCategory("의류");

        var body = Map.of("name", "티셔츠", "parentId", parentId);

        var response = client.post()
                .uri("/api/v1/admin/categories")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(CategoryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).isEqualTo("티셔츠");
    }

    @Test
    @DisplayName("카테고리 생성 API가 존재하지 않는 parent_id를 404 CATEGORY_NOT_FOUND로 거부한다")
    void createCategoryWithNonExistentParentId() {
        var body = Map.of("name", "티셔츠", "parentId", 99999L);

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("CATEGORY_NOT_FOUND");
    }

    @Test
    @DisplayName("카테고리 생성 API가 ADMIN 권한 없이 403을 반환한다")
    void createCategoryWithoutAdmin() {
        var body = Map.of("name", "의류");

        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + createCustomerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    // ==================== 카테고리 목록 조회 ====================

    @Test
    @DisplayName("카테고리 목록 API가 부모-자식 계층 트리 구조로 반환한다")
    void getCategoriesReturnsTreeStructure() {
        Long parentId = createCategory("의류");
        createCategoryWithParent("티셔츠", parentId);

        var response = client.get()
                .uri("/api/v1/categories")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<CategoryResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).name()).isEqualTo("의류");
        assertThat(response.getBody().get(0).children()).hasSize(1);
        assertThat(response.getBody().get(0).children().get(0).name()).isEqualTo("티셔츠");
        assertThat(response.getBody().get(0).children().get(0).children()).isEmpty();
    }

    @Test
    @DisplayName("카테고리 목록 API가 카테고리가 없으면 빈 배열을 반환한다")
    void getCategoriesReturnsEmptyWhenNone() {
        var response = client.get()
                .uri("/api/v1/categories")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<CategoryResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    // ==================== 상품 생성 ====================

    @Test
    @DisplayName("상품 생성 API가 유효한 정보로 상품을 등록한다")
    void createProductWithValidInfo() {
        Long categoryId = createCategory("의류");

        var body = Map.of("name", "티셔츠", "categoryId", categoryId, "description", "편한 티셔츠");

        var response = client.post()
                .uri("/api/v1/admin/products")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("티셔츠");
        assertThat(response.getBody().status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(response.getBody().categoryId()).isEqualTo(categoryId);
    }

    @Test
    @DisplayName("상품 생성 API가 존재하지 않는 category_id를 404 CATEGORY_NOT_FOUND로 거부한다")
    void createProductWithNonExistentCategory() {
        var body = Map.of("name", "티셔츠", "categoryId", 99999L);

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("CATEGORY_NOT_FOUND");
    }

    @Test
    @DisplayName("상품 생성 API가 ADMIN 권한 없이 403을 반환한다")
    void createProductWithoutAdmin() {
        Long categoryId = createCategory("의류");

        var body = Map.of("name", "티셔츠", "categoryId", categoryId);

        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + createCustomerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    @Test
    @DisplayName("상품 생성 API가 imageUrl을 저장하고 응답에 포함한다")
    void createProductWithImageUrl() {
        Long categoryId = createCategory("의류");

        var body = Map.of("name", "티셔츠", "categoryId", categoryId, "imageUrl", "https://cdn.example.com/tshirt.jpg");

        var response = client.post()
                .uri("/api/v1/admin/products")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().imageUrl()).isEqualTo("https://cdn.example.com/tshirt.jpg");
    }

    @Test
    @DisplayName("상품 생성 API가 imageUrl 없이도 상품을 등록한다 (nullable)")
    void createProductWithoutImageUrl() {
        Long categoryId = createCategory("의류");

        var body = Map.of("name", "티셔츠", "categoryId", categoryId);

        var response = client.post()
                .uri("/api/v1/admin/products")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().imageUrl()).isNull();
    }

    // ==================== 상품 목록 조회 ====================

    @Test
    @DisplayName("상품 목록 API가 keyword로 상품명 부분 일치 검색한다")
    void getProductsFilterByKeyword() {
        Long categoryId = createCategory("의류");
        createProduct("화이트 티셔츠", categoryId);
        createProduct("블랙 후드티", categoryId);

        var response = client.get()
                .uri("/api/v1/products?keyword=티셔츠")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<ProductResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).hasSize(1);
        assertThat(response.getBody().content().get(0).name()).isEqualTo("화이트 티셔츠");
    }

    @Test
    @DisplayName("상품 목록 API가 keyword가 없으면 전체 상품을 반환한다")
    void getProductsWithoutKeywordReturnsAll() {
        Long categoryId = createCategory("의류");
        createProduct("화이트 티셔츠", categoryId);
        createProduct("블랙 후드티", categoryId);

        var response = client.get()
                .uri("/api/v1/products")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<ProductResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).hasSize(2);
    }

    @Test
    @DisplayName("상품 목록 API가 keyword와 categoryId를 AND 조건으로 함께 필터링한다")
    void getProductsFilterByKeywordAndCategoryId() {
        Long catA = createCategory("카테고리A");
        Long catB = createCategory("카테고리B");
        createProduct("화이트 티셔츠", catA);
        createProduct("블랙 티셔츠", catB);

        var response = client.get()
                .uri("/api/v1/products?keyword=티셔츠&categoryId=" + catA)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<ProductResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).hasSize(1);
        assertThat(response.getBody().content().get(0).name()).isEqualTo("화이트 티셔츠");
    }

    @Test
    @DisplayName("상품 목록 API가 응답에 imageUrl을 포함한다")
    void getProductsResponseIncludesImageUrl() {
        Long categoryId = createCategory("의류");

        var body = Map.of("name", "티셔츠", "categoryId", categoryId, "imageUrl", "https://cdn.example.com/tshirt.jpg");
        client.post()
                .uri("/api/v1/admin/products")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        var response = client.get()
                .uri("/api/v1/products")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<ProductResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).hasSize(1);
        assertThat(response.getBody().content().get(0).imageUrl()).isEqualTo("https://cdn.example.com/tshirt.jpg");
    }

    @Test
    @DisplayName("상품 목록 API가 상품이 없으면 빈 배열을 반환한다")
    void getProductsReturnsEmptyWhenNone() {
        var response = client.get()
                .uri("/api/v1/products")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<ProductResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).isEmpty();
        assertThat(response.getBody().totalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("상품 목록 API가 ON_SALE 상품을 반환한다")
    void getProductsReturnsOnSaleProducts() {
        Long categoryId = createCategory("의류");
        createProduct("티셔츠", categoryId);

        var response = client.get()
                .uri("/api/v1/products")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<ProductResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).hasSize(1);
        assertThat(response.getBody().content().get(0).name()).isEqualTo("티셔츠");
        assertThat(response.getBody().content().get(0).status()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("상품 목록 API가 STOPPED 상품을 제외한다")
    void getProductsExcludesStoppedProducts() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        // STOPPED 상태로 변경
        client.put()
                .uri("/api/v1/admin/products/" + productId)
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "티셔츠", "status", "STOPPED", "categoryId", categoryId))
                .retrieve()
                .toBodilessEntity();

        var response = client.get()
                .uri("/api/v1/products")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<ProductResponse>>() {});

        assertThat(response.getBody().content()).isEmpty();
    }

    @Test
    @DisplayName("상품 목록 API가 deleted 상품을 제외한다")
    void getProductsExcludesDeletedProducts() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        // 삭제
        client.delete()
                .uri("/api/v1/admin/products/" + productId)
                .header("Authorization", "Bearer " + createAdminToken())
                .retrieve()
                .toBodilessEntity();

        var response = client.get()
                .uri("/api/v1/products")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<ProductResponse>>() {});

        assertThat(response.getBody().content()).isEmpty();
    }

    @Test
    @DisplayName("상품 목록 API가 모든 Variant가 SOLD_OUT인 상품도 목록에 포함한다")
    void getProductsIncludesProductWithAllVariantsSoldOut() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        createVariant(productId, 10000L, 0, null);  // SOLD_OUT

        var response = client.get()
                .uri("/api/v1/products")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<ProductResponse>>() {});

        assertThat(response.getBody().content()).hasSize(1);
        assertThat(response.getBody().content().get(0).name()).isEqualTo("티셔츠");
    }

    @Test
    @DisplayName("상품 목록 API가 created_at DESC 순으로 반환한다")
    void getProductsOrderByCreatedAtDesc() throws InterruptedException {
        Long categoryId = createCategory("의류");
        createProduct("티셔츠A", categoryId);
        Thread.sleep(10);
        createProduct("티셔츠B", categoryId);

        var response = client.get()
                .uri("/api/v1/products")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<ProductResponse>>() {});

        List<ProductResponse> content = response.getBody().content();
        assertThat(content).hasSize(2);
        assertThat(content.get(0).name()).isEqualTo("티셔츠B");
        assertThat(content.get(1).name()).isEqualTo("티셔츠A");
    }

    @Test
    @DisplayName("상품 목록 API가 category_id로 필터링한다")
    void getProductsFilterByCategoryId() {
        Long cat1 = createCategory("의류");
        Long cat2 = createCategory("전자기기");
        createProduct("티셔츠", cat1);
        createProduct("노트북", cat2);

        var response = client.get()
                .uri("/api/v1/products?categoryId=" + cat1)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<ProductResponse>>() {});

        assertThat(response.getBody().content()).hasSize(1);
        assertThat(response.getBody().content().get(0).name()).isEqualTo("티셔츠");
    }

    @Test
    @DisplayName("상품 목록 API가 page/size로 페이지네이션한다")
    void getProductsPagination() {
        Long categoryId = createCategory("의류");
        for (int i = 1; i <= 5; i++) {
            createProduct("상품" + i, categoryId);
        }

        var response = client.get()
                .uri("/api/v1/products?page=0&size=3")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PageResponse<ProductResponse>>() {});

        assertThat(response.getBody().content()).hasSize(3);
        assertThat(response.getBody().totalElements()).isEqualTo(5);
        assertThat(response.getBody().totalPages()).isEqualTo(2);
    }

    // ==================== 상품 상세 조회 ====================

    @Test
    @DisplayName("상품 상세 API가 Product, Options, Variants를 포함해 반환한다")
    void getProductDetailReturnsFullInfo() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long optionId = addOption(productId, "COLOR", List.of("BLACK", "WHITE"));

        // optionValue ID 조회를 위해 상세 조회
        var detail = client.get()
                .uri("/api/v1/products/" + productId)
                .retrieve()
                .toEntity(ProductDetailResponse.class).getBody();

        List<Long> valueIds = detail.options().stream()
                .flatMap(o -> o.values().stream())
                .map(v -> {
                    // DB에서 optionValue 찾기는 허용 (HTTP로 옵션 값 ID 노출 안 됨)
                    return productOptionValueRepository.findByOptionId(optionId).stream()
                            .filter(ov -> ov.getValue().equals(v))
                            .map(ov -> ov.getId())
                            .findFirst().orElseThrow();
                })
                .toList();

        createVariant(productId, 10000L, 5, List.of(valueIds.get(0)));

        var response = client.get()
                .uri("/api/v1/products/" + productId)
                .retrieve()
                .toEntity(ProductDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(productId);
        assertThat(response.getBody().options()).hasSize(1);
        assertThat(response.getBody().options().get(0).name()).isEqualTo("COLOR");
        assertThat(response.getBody().options().get(0).values()).containsExactlyInAnyOrder("BLACK", "WHITE");
        assertThat(response.getBody().variants()).hasSize(1);
        assertThat(response.getBody().variants().get(0).price()).isEqualTo(10000L);
        assertThat(response.getBody().variants().get(0).stock()).isEqualTo(5);
        assertThat(response.getBody().variants().get(0).status()).isEqualTo(VariantStatus.ON_SALE);
    }

    @Test
    @DisplayName("상품 상세 API가 존재하지 않는 상품에 404 PRODUCT_NOT_FOUND를 반환한다")
    void getProductDetailForNonExistentProduct() {
        var ex = catchThrowableOfType(
                () -> client.get()
                        .uri("/api/v1/products/99999")
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("상품 상세 API가 deleted 상품에 404 PRODUCT_NOT_FOUND를 반환한다")
    void getProductDetailForDeletedProduct() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        client.delete()
                .uri("/api/v1/admin/products/" + productId)
                .header("Authorization", "Bearer " + createAdminToken())
                .retrieve()
                .toBodilessEntity();

        var ex = catchThrowableOfType(
                () -> client.get()
                        .uri("/api/v1/products/" + productId)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("상품 상세 API가 STOPPED 상품에 404 PRODUCT_NOT_FOUND를 반환한다")
    void getProductDetailForStoppedProduct() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        client.put()
                .uri("/api/v1/admin/products/" + productId)
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "티셔츠", "status", "STOPPED", "categoryId", categoryId))
                .retrieve()
                .toBodilessEntity();

        var ex = catchThrowableOfType(
                () -> client.get()
                        .uri("/api/v1/products/" + productId)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("상품 상세 API가 응답에 imageUrl을 포함한다")
    void getProductDetailResponseIncludesImageUrl() {
        Long categoryId = createCategory("의류");

        var createBody = Map.of("name", "티셔츠", "categoryId", categoryId, "imageUrl", "https://cdn.example.com/tshirt.jpg");
        var createResponse = client.post()
                .uri("/api/v1/admin/products")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(createBody)
                .retrieve()
                .toEntity(ProductResponse.class);
        Long productId = createResponse.getBody().id();

        var response = client.get()
                .uri("/api/v1/products/" + productId)
                .retrieve()
                .toEntity(ProductDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().imageUrl()).isEqualTo("https://cdn.example.com/tshirt.jpg");
    }

    // ==================== 상품 수정 ====================

    @Test
    @DisplayName("상품 수정 API가 imageUrl을 수정하고 응답에 포함한다")
    void updateProductImageUrl() {
        Long categoryId = createCategory("의류");

        var createBody = Map.of("name", "티셔츠", "categoryId", categoryId, "imageUrl", "old.jpg");
        var createResponse = client.post()
                .uri("/api/v1/admin/products")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(createBody)
                .retrieve()
                .toEntity(ProductResponse.class);
        Long productId = createResponse.getBody().id();

        var updateBody = Map.of("name", "티셔츠", "status", "ON_SALE", "categoryId", categoryId, "imageUrl", "new.jpg");
        var response = client.put()
                .uri("/api/v1/admin/products/" + productId)
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(updateBody)
                .retrieve()
                .toEntity(ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().imageUrl()).isEqualTo("new.jpg");
    }

    @Test
    @DisplayName("상품 수정 API가 name, description, status, category_id를 수정한다")
    void updateProductFields() {
        Long categoryId = createCategory("의류");
        Long newCategoryId = createCategory("전자기기");
        Long productId = createProduct("티셔츠", categoryId);

        var response = client.put()
                .uri("/api/v1/admin/products/" + productId)
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "후드티", "description", "따뜻해요", "status", "STOPPED", "categoryId", newCategoryId))
                .retrieve()
                .toEntity(ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("후드티");
        assertThat(response.getBody().description()).isEqualTo("따뜻해요");
        assertThat(response.getBody().status()).isEqualTo(ProductStatus.STOPPED);
        assertThat(response.getBody().categoryId()).isEqualTo(newCategoryId);
    }

    @Test
    @DisplayName("상품 수정 API가 존재하지 않는 상품에 404 PRODUCT_NOT_FOUND를 반환한다")
    void updateNonExistentProduct() {
        Long categoryId = createCategory("의류");

        var ex = catchThrowableOfType(
                () -> client.put()
                        .uri("/api/v1/admin/products/99999")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("name", "후드티", "status", "ON_SALE", "categoryId", categoryId))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("상품 수정 API가 deleted 상품에 404 PRODUCT_NOT_FOUND를 반환한다")
    void updateDeletedProduct() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        client.delete()
                .uri("/api/v1/admin/products/" + productId)
                .header("Authorization", "Bearer " + createAdminToken())
                .retrieve()
                .toBodilessEntity();

        var ex = catchThrowableOfType(
                () -> client.put()
                        .uri("/api/v1/admin/products/" + productId)
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("name", "후드티", "status", "ON_SALE", "categoryId", categoryId))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("상품 수정 API가 ADMIN 권한 없이 403을 반환한다")
    void updateProductWithoutAdmin() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        assertThatThrownBy(() ->
                client.put()
                        .uri("/api/v1/admin/products/" + productId)
                        .header("Authorization", "Bearer " + createCustomerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("name", "후드티", "status", "ON_SALE", "categoryId", categoryId))
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    @Test
    @DisplayName("상품 수정 API가 STOPPED 상품을 정상적으로 수정한다")
    void update_stopped_product_succeeds() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        client.put()
                .uri("/api/v1/admin/products/" + productId)
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "티셔츠", "status", "STOPPED", "categoryId", categoryId))
                .retrieve()
                .toBodilessEntity();

        var response = client.put()
                .uri("/api/v1/admin/products/" + productId)
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "후드티", "status", "STOPPED", "categoryId", categoryId))
                .retrieve()
                .toEntity(ProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("후드티");
    }

    // ==================== 상품 삭제 ====================

    @Test
    @DisplayName("상품 삭제 API가 논리 삭제(deleted=true)로 처리한다")
    void deleteProductSoftDeletes() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        var response = client.delete()
                .uri("/api/v1/admin/products/" + productId)
                .header("Authorization", "Bearer " + createAdminToken())
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 삭제 후 상세 조회 시 404 반환
        var ex = catchThrowableOfType(
                () -> client.get()
                        .uri("/api/v1/products/" + productId)
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );
        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("상품 삭제 API가 존재하지 않는 상품에 404 PRODUCT_NOT_FOUND를 반환한다")
    void deleteNonExistentProduct() {
        var ex = catchThrowableOfType(
                () -> client.delete()
                        .uri("/api/v1/admin/products/99999")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("상품 삭제 API가 이미 삭제된 상품에 404 PRODUCT_NOT_FOUND를 반환한다")
    void deleteAlreadyDeletedProduct() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        client.delete()
                .uri("/api/v1/admin/products/" + productId)
                .header("Authorization", "Bearer " + createAdminToken())
                .retrieve()
                .toBodilessEntity();

        var ex = catchThrowableOfType(
                () -> client.delete()
                        .uri("/api/v1/admin/products/" + productId)
                        .header("Authorization", "Bearer " + createAdminToken())
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("상품 삭제 API가 ADMIN 권한 없이 403을 반환한다")
    void deleteProductWithoutAdmin() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        assertThatThrownBy(() ->
                client.delete()
                        .uri("/api/v1/admin/products/" + productId)
                        .header("Authorization", "Bearer " + createCustomerToken())
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    // ==================== 옵션 추가 ====================

    @Test
    @DisplayName("옵션 추가 API가 상품에 옵션명과 값을 추가한다")
    void addOptionToProduct() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        var response = client.post()
                .uri("/api/v1/admin/products/" + productId + "/options")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "COLOR", "values", List.of("BLACK", "WHITE")))
                .retrieve()
                .toEntity(OptionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).isEqualTo("COLOR");
        assertThat(response.getBody().values()).containsExactlyInAnyOrder("BLACK", "WHITE");
    }

    @Test
    @DisplayName("옵션 추가 API가 동일 상품 내 옵션명 중복 시 409 OPTION_DUPLICATE를 반환한다")
    void addDuplicateOptionReturnsConflict() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        addOption(productId, "COLOR", List.of("BLACK"));

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/products/" + productId + "/options")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("name", "COLOR", "values", List.of("RED")))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Conflict.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("OPTION_DUPLICATE");
    }

    @Test
    @DisplayName("옵션 추가 API가 존재하지 않는 상품에 404 PRODUCT_NOT_FOUND를 반환한다")
    void addOptionToNonExistentProduct() {
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/products/99999/options")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("name", "COLOR", "values", List.of("BLACK")))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("옵션 추가 API가 deleted 상품에 404 PRODUCT_NOT_FOUND를 반환한다")
    void addOptionToDeletedProductReturns404() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        client.delete()
                .uri("/api/v1/admin/products/" + productId)
                .header("Authorization", "Bearer " + createAdminToken())
                .retrieve()
                .toBodilessEntity();

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/products/" + productId + "/options")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("name", "COLOR", "values", List.of("BLACK")))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("옵션 추가 API가 ADMIN 권한 없이 403을 반환한다")
    void addOptionWithoutAdmin() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/admin/products/" + productId + "/options")
                        .header("Authorization", "Bearer " + createCustomerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("name", "COLOR", "values", List.of("BLACK")))
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    // ==================== Variant 생성 ====================

    @Test
    @DisplayName("Variant 생성 API가 가격과 재고로 Variant를 생성한다")
    void createVariantWithPriceAndStock() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        var response = client.post()
                .uri("/api/v1/admin/products/" + productId + "/variants")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("price", 10000L, "stock", 5))
                .retrieve()
                .toEntity(VariantResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().price()).isEqualTo(10000L);
        assertThat(response.getBody().stock()).isEqualTo(5);
        assertThat(response.getBody().status()).isEqualTo(VariantStatus.ON_SALE);
    }

    @Test
    @DisplayName("Variant 생성 API가 재고 0인 Variant를 SOLD_OUT 상태로 생성한다")
    void createVariantWithZeroStockCreatesSoldOut() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        var response = client.post()
                .uri("/api/v1/admin/products/" + productId + "/variants")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("price", 10000L, "stock", 0))
                .retrieve()
                .toEntity(VariantResponse.class);

        assertThat(response.getBody().status()).isEqualTo(VariantStatus.SOLD_OUT);
        assertThat(response.getBody().stock()).isEqualTo(0);
    }

    @Test
    @DisplayName("Variant 생성 API가 재고가 있는 Variant를 ON_SALE 상태로 생성한다")
    void variant_created_with_positive_stock_is_on_sale() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        var response = client.post()
                .uri("/api/v1/admin/products/" + productId + "/variants")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("price", 10000L, "stock", 5))
                .retrieve()
                .toEntity(VariantResponse.class);

        assertThat(response.getBody().status()).isEqualTo(VariantStatus.ON_SALE);
        assertThat(response.getBody().stock()).isEqualTo(5);
    }

    @Test
    @DisplayName("Variant 생성 API가 동일 옵션 조합 중복 시 409 DUPLICATE_VARIANT를 반환한다")
    void createDuplicateVariantReturnsConflict() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long optionId = addOption(productId, "COLOR", List.of("BLACK"));

        Long optionValueId = productOptionValueRepository.findByOptionId(optionId).get(0).getId();

        createVariant(productId, 10000L, 5, List.of(optionValueId));

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/products/" + productId + "/variants")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("price", 15000L, "stock", 3, "optionValueIds", List.of(optionValueId)))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.Conflict.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("DUPLICATE_VARIANT");
    }

    @Test
    @DisplayName("Variant 생성 API가 존재하지 않는 상품에 404 PRODUCT_NOT_FOUND를 반환한다")
    void createVariantForNonExistentProduct() {
        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/products/99999/variants")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("price", 10000L, "stock", 5))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("Variant 생성 API가 존재하지 않는 optionValueId 요청 시 404 OPTION_VALUE_NOT_FOUND를 반환한다")
    void createVariantWithNonExistentOptionValueReturns404() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        var ex = catchThrowableOfType(
                () -> client.post()
                        .uri("/api/v1/admin/products/" + productId + "/variants")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("price", 10000L, "stock", 5, "optionValueIds", List.of(99999L)))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("OPTION_VALUE_NOT_FOUND");
    }

    @Test
    @DisplayName("Variant 생성 API가 ADMIN 권한 없이 403을 반환한다")
    void createVariantWithoutAdmin() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);

        assertThatThrownBy(() ->
                client.post()
                        .uri("/api/v1/admin/products/" + productId + "/variants")
                        .header("Authorization", "Bearer " + createCustomerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("price", 10000L, "stock", 5))
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    // ==================== Variant 수정 ====================

    @Test
    @DisplayName("Variant 수정 API가 price와 status를 수정한다")
    void updateVariantPriceAndStatus() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5, null);

        var response = client.put()
                .uri("/api/v1/admin/variants/" + variantId)
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("price", 15000L, "status", "STOPPED"))
                .retrieve()
                .toEntity(VariantResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().price()).isEqualTo(15000L);
        assertThat(response.getBody().status()).isEqualTo(VariantStatus.STOPPED);
    }

    @Test
    @DisplayName("Variant 수정 API가 존재하지 않는 Variant에 404 VARIANT_NOT_FOUND를 반환한다")
    void updateNonExistentVariant() {
        var ex = catchThrowableOfType(
                () -> client.put()
                        .uri("/api/v1/admin/variants/99999")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("price", 15000L, "status", "ON_SALE"))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("VARIANT_NOT_FOUND");
    }

    @Test
    @DisplayName("Variant 수정 API가 ADMIN 권한 없이 403을 반환한다")
    void updateVariantWithoutAdmin() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5, null);

        assertThatThrownBy(() ->
                client.put()
                        .uri("/api/v1/admin/variants/" + variantId)
                        .header("Authorization", "Bearer " + createCustomerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("price", 15000L, "status", "ON_SALE"))
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    // ==================== 재고 수정 ====================

    @Test
    @DisplayName("재고 수정 API가 stock을 변경한다")
    void updateStockChangesStock() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5, null);

        var response = client.put()
                .uri("/api/v1/admin/variants/" + variantId + "/stock")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("stock", 10))
                .retrieve()
                .toEntity(VariantResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("재고 수정 API가 stock을 0으로 변경 시 SOLD_OUT으로 자동 전환한다")
    void updateStockToZeroChangesSoldOut() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5, null);

        var response = client.put()
                .uri("/api/v1/admin/variants/" + variantId + "/stock")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("stock", 0))
                .retrieve()
                .toEntity(VariantResponse.class);

        assertThat(response.getBody().stock()).isEqualTo(0);
        assertThat(response.getBody().status()).isEqualTo(VariantStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("재고 수정 API가 stock을 양수로 복구 시 ON_SALE로 자동 전환한다")
    void updateStockToPositiveChangesOnSale() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 0, null);  // SOLD_OUT으로 시작

        var response = client.put()
                .uri("/api/v1/admin/variants/" + variantId + "/stock")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("stock", 10))
                .retrieve()
                .toEntity(VariantResponse.class);

        assertThat(response.getBody().stock()).isEqualTo(10);
        assertThat(response.getBody().status()).isEqualTo(VariantStatus.ON_SALE);
    }

    @Test
    @DisplayName("재고 수정 API가 음수 재고 요청을 거부한다")
    void updateStockWithNegativeValueRejects() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5, null);

        assertThatThrownBy(() ->
                client.put()
                        .uri("/api/v1/admin/variants/" + variantId + "/stock")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("stock", -1))
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    @DisplayName("재고 수정 API가 존재하지 않는 Variant에 404 VARIANT_NOT_FOUND를 반환한다")
    void updateStockForNonExistentVariant() {
        var ex = catchThrowableOfType(
                () -> client.put()
                        .uri("/api/v1/admin/variants/99999/stock")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("stock", 10))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("VARIANT_NOT_FOUND");
    }

    @Test
    @DisplayName("재고 수정 API가 ADMIN 권한 없이 403을 반환한다")
    void updateStockWithoutAdmin() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5, null);

        assertThatThrownBy(() ->
                client.put()
                        .uri("/api/v1/admin/variants/" + variantId + "/stock")
                        .header("Authorization", "Bearer " + createCustomerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("stock", 10))
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    // ==================== 재고 차감 ====================

    @Test
    @DisplayName("재고 차감 API가 stock을 정상 차감하고 잔여 재고를 반환한다")
    void decreaseStockSuccessAndReturnsRemainingStock() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10, null);

        var response = client.put()
                .uri("/api/v1/admin/variants/" + variantId + "/stock/decrease")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", 3))
                .retrieve()
                .toEntity(VariantResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().stock()).isEqualTo(7);
        assertThat(response.getBody().status()).isEqualTo(VariantStatus.ON_SALE);
    }

    @Test
    @DisplayName("재고 차감 API가 재고 부족 시 400 OUT_OF_STOCK을 반환한다")
    void decreaseStockWithInsufficientStockReturns400() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5, null);

        var ex = catchThrowableOfType(
                () -> client.put()
                        .uri("/api/v1/admin/variants/" + variantId + "/stock/decrease")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("quantity", 10))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.BadRequest.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("OUT_OF_STOCK");
    }

    @Test
    @DisplayName("재고 차감 API가 stock을 0으로 만들면 Variant 상태를 SOLD_OUT으로 전환한다")
    void decreaseStockToZeroTransitionsToSoldOut() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5, null);

        var response = client.put()
                .uri("/api/v1/admin/variants/" + variantId + "/stock/decrease")
                .header("Authorization", "Bearer " + createAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", 5))
                .retrieve()
                .toEntity(VariantResponse.class);

        assertThat(response.getBody().stock()).isEqualTo(0);
        assertThat(response.getBody().status()).isEqualTo(VariantStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("재고 차감 API가 존재하지 않는 Variant에 404 VARIANT_NOT_FOUND를 반환한다")
    void decreaseStockForNonExistentVariantReturns404() {
        var ex = catchThrowableOfType(
                () -> client.put()
                        .uri("/api/v1/admin/variants/99999/stock/decrease")
                        .header("Authorization", "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("quantity", 1))
                        .retrieve()
                        .toBodilessEntity(),
                HttpClientErrorException.NotFound.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getResponseBodyAsString()).contains("VARIANT_NOT_FOUND");
    }

    @Test
    @DisplayName("재고 차감 API가 ADMIN 권한 없이 403을 반환한다")
    void decreaseStockWithoutAdminReturns403() {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10, null);

        assertThatThrownBy(() ->
                client.put()
                        .uri("/api/v1/admin/variants/" + variantId + "/stock/decrease")
                        .header("Authorization", "Bearer " + createCustomerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("quantity", 1))
                        .retrieve()
                        .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    // ==================== 재고 동시성 ====================

    @Test
    @DisplayName("동시 재고 차감 요청에서 정확한 수량만 차감된다")
    void concurrentStockDecreaseIsAccurate() throws InterruptedException {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 10, null);

        int threadCount = 15;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        String adminToken = createAdminToken();
        RestClient concurrentClient = RestClient.create("http://localhost:" + port);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    concurrentClient.put()
                            .uri("/api/v1/admin/variants/" + variantId + "/stock/decrease")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("quantity", 1))
                            .retrieve()
                            .toBodilessEntity();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 재고 부족 시 실패 (정상)
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        var detail = client.get()
                .uri("/api/v1/products/" + productId)
                .retrieve()
                .toEntity(ProductDetailResponse.class).getBody();

        int remainingStock = detail.variants().get(0).stock();
        assertThat(remainingStock).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(10);
    }

    @Test
    @DisplayName("동시 재고 차감 요청에서 재고 초과분은 실패하고 성공 건만 재고를 차감한다")
    void concurrent_stock_decrease_fails_for_excess_requests() throws InterruptedException {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5, null);

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        String adminToken = createAdminToken();
        RestClient concurrentClient = RestClient.create("http://localhost:" + port);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    concurrentClient.put()
                            .uri("/api/v1/admin/variants/" + variantId + "/stock/decrease")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("quantity", 1))
                            .retrieve()
                            .toBodilessEntity();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("동시 재고 차감 완료 후 최종 재고가 0이다")
    void concurrent_stock_decrease_leaves_zero_stock() throws InterruptedException {
        Long categoryId = createCategory("의류");
        Long productId = createProduct("티셔츠", categoryId);
        Long variantId = createVariant(productId, 10000L, 5, null);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        String adminToken = createAdminToken();
        RestClient concurrentClient = RestClient.create("http://localhost:" + port);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    concurrentClient.put()
                            .uri("/api/v1/admin/variants/" + variantId + "/stock/decrease")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("quantity", 1))
                            .retrieve()
                            .toBodilessEntity();
                } catch (Exception e) {
                    // 재고 초과 실패는 정상
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        int finalStock = productVariantRepository.findById(variantId).get().getStock();
        assertThat(finalStock).isEqualTo(0);
    }

}