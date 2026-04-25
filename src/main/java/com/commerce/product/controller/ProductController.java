package com.commerce.product.controller;

import com.commerce.product.dto.*;
import com.commerce.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ==================== 상품 CRUD ====================

    @PostMapping("/api/v1/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @GetMapping("/api/v1/products")
    public ResponseEntity<PageResponse<ProductResponse>> getProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(productService.getProducts(categoryId, page, size));
    }

    @GetMapping("/api/v1/products/{id}")
    public ResponseEntity<ProductDetailResponse> getProductDetail(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductDetail(id));
    }

    @PutMapping("/api/v1/admin/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/api/v1/admin/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 옵션 ====================

    @PostMapping("/api/v1/admin/products/{id}/options")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OptionResponse> addOption(
            @PathVariable Long id,
            @Valid @RequestBody AddOptionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.addOption(id, request));
    }

    // ==================== Variant ====================

    @PostMapping("/api/v1/admin/products/{id}/variants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VariantResponse> createVariant(
            @PathVariable Long id,
            @Valid @RequestBody CreateVariantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createVariant(id, request));
    }

    @PutMapping("/api/v1/admin/variants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VariantResponse> updateVariant(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVariantRequest request
    ) {
        return ResponseEntity.ok(productService.updateVariant(id, request));
    }

    @PutMapping("/api/v1/admin/variants/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VariantResponse> updateStock(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockRequest request
    ) {
        return ResponseEntity.ok(productService.updateStock(id, request));
    }

    @PutMapping("/api/v1/admin/variants/{id}/stock/decrease")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VariantResponse> decreaseStock(
            @PathVariable Long id,
            @Valid @RequestBody DecreaseStockRequest request
    ) {
        return ResponseEntity.ok(productService.decreaseStock(id, request));
    }
}