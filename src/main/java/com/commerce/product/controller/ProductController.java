package com.commerce.product.controller;

import com.commerce.global.exception.ErrorResponse;
import com.commerce.product.dto.*;
import com.commerce.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "상품", description = "상품, 옵션, Variant 관리 및 조회 API")
@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ==================== 상품 CRUD ====================

    @Operation(summary = "상품 생성", description = "새 상품을 등록합니다. ADMIN 권한 필요.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "상품 생성 성공",
            content = @Content(schema = @Schema(implementation = ProductResponse.class))),
        @ApiResponse(responseCode = "400", description = "유효성 검사 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 카테고리",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/api/v1/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @Operation(summary = "상품 목록 조회", description = "ON_SALE 상품을 최신순(created_at DESC)으로 페이지네이션하여 반환합니다. STOPPED/삭제 상품은 제외됩니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/api/v1/products")
    public ResponseEntity<PageResponse<ProductResponse>> getProducts(
            @Parameter(description = "카테고리 ID 필터 (없으면 전체 조회)", example = "1")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(productService.getProducts(categoryId, page, size));
    }

    @Operation(summary = "상품 상세 조회", description = "상품 정보와 옵션, Variant 목록을 반환합니다. STOPPED/삭제 상품은 404를 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않거나 비노출 상품",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/api/v1/products/{id}")
    public ResponseEntity<ProductDetailResponse> getProductDetail(
            @Parameter(description = "상품 ID", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductDetail(id));
    }

    @Operation(summary = "상품 수정", description = "상품의 이름, 설명, 상태, 카테고리를 수정합니다. ADMIN 권한 필요.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공",
            content = @Content(schema = @Schema(implementation = ProductResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제된 상품",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/api/v1/admin/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(
            @Parameter(description = "상품 ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @Operation(summary = "상품 삭제", description = "상품을 논리 삭제(deleted=true)합니다. ADMIN 권한 필요.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 성공"),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않거나 이미 삭제된 상품",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/api/v1/admin/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "상품 ID", example = "1")
            @PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 옵션 ====================

    @Operation(summary = "옵션 추가", description = "상품에 옵션명과 옵션값들을 추가합니다. 동일 상품 내 옵션명은 중복 불가. ADMIN 권한 필요.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "옵션 추가 성공",
            content = @Content(schema = @Schema(implementation = OptionResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 상품",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "옵션명 중복",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/api/v1/admin/products/{id}/options")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OptionResponse> addOption(
            @Parameter(description = "상품 ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody AddOptionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.addOption(id, request));
    }

    // ==================== Variant ====================

    @Operation(summary = "Variant 생성", description = "가격, 재고, 옵션값 조합으로 Variant를 생성합니다. 재고 0이면 SOLD_OUT 상태로 생성됩니다. ADMIN 권한 필요.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Variant 생성 성공",
            content = @Content(schema = @Schema(implementation = VariantResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 상품 또는 옵션값",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "동일 옵션 조합 중복",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/api/v1/admin/products/{id}/variants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VariantResponse> createVariant(
            @Parameter(description = "상품 ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody CreateVariantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createVariant(id, request));
    }

    @Operation(summary = "Variant 수정", description = "Variant의 가격과 상태를 수정합니다. ADMIN 권한 필요.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공",
            content = @Content(schema = @Schema(implementation = VariantResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 Variant",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/api/v1/admin/variants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VariantResponse> updateVariant(
            @Parameter(description = "Variant ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateVariantRequest request
    ) {
        return ResponseEntity.ok(productService.updateVariant(id, request));
    }

    @Operation(summary = "재고 수정", description = "Variant의 재고를 직접 설정합니다. 0이면 SOLD_OUT, 양수면 ON_SALE로 자동 전환됩니다. ADMIN 권한 필요.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "재고 수정 성공",
            content = @Content(schema = @Schema(implementation = VariantResponse.class))),
        @ApiResponse(responseCode = "400", description = "유효성 검사 실패 (음수 재고)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 Variant",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/api/v1/admin/variants/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VariantResponse> updateStock(
            @Parameter(description = "Variant ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockRequest request
    ) {
        return ResponseEntity.ok(productService.updateStock(id, request));
    }

    @Operation(summary = "재고 차감", description = "Variant 재고를 원자적으로 차감합니다. 재고 부족 시 400을 반환합니다. ADMIN 권한 필요.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "재고 차감 성공",
            content = @Content(schema = @Schema(implementation = VariantResponse.class))),
        @ApiResponse(responseCode = "400", description = "재고 부족",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 Variant",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/api/v1/admin/variants/{id}/stock/decrease")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VariantResponse> decreaseStock(
            @Parameter(description = "Variant ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody DecreaseStockRequest request
    ) {
        return ResponseEntity.ok(productService.decreaseStock(id, request));
    }
}