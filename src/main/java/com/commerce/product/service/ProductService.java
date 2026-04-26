package com.commerce.product.service;

import com.commerce.global.exception.BusinessException;
import com.commerce.global.exception.ErrorCode;
import com.commerce.product.domain.*;
import com.commerce.product.dto.*;
import com.commerce.product.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductOptionValueRepository productOptionValueRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductVariantOptionRepository productVariantOptionRepository;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        Product product = Product.create(request.name(), request.description(), category);
        productRepository.save(product);

        return toProductResponse(product);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getProducts(Long categoryId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> result = productRepository.findAllVisible(ProductStatus.ON_SALE, categoryId, pageable);

        List<ProductResponse> content = result.getContent().stream()
                .map(this::toProductResponse)
                .toList();

        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetail(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.isVisible()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        return buildProductDetail(product);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.isDeleted()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        product.update(request.name(), request.description(), request.status(), category);
        return toProductResponse(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.isDeleted()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        product.delete();
    }

    @Transactional
    public OptionResponse addOption(Long productId, AddOptionRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.isDeleted()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        if (productOptionRepository.existsByProductIdAndName(productId, request.name())) {
            throw new BusinessException(ErrorCode.OPTION_DUPLICATE);
        }

        ProductOption option = ProductOption.create(product, request.name());
        for (String value : request.values()) {
            option.addValue(value);
        }
        productOptionRepository.save(option);

        List<String> values = option.getValues().stream()
                .map(ProductOptionValue::getValue)
                .toList();
        return new OptionResponse(option.getId(), option.getName(), values);
    }

    @Transactional
    public VariantResponse createVariant(Long productId, CreateVariantRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.isDeleted()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 중복 옵션 조합 체크
        List<Long> requestedValueIds = request.optionValueIds() != null ? request.optionValueIds() : List.of();
        if (!requestedValueIds.isEmpty()) {
            checkDuplicateVariant(productId, requestedValueIds);
        }

        ProductVariant variant = ProductVariant.create(product, request.price(), request.stock());
        productVariantRepository.save(variant);

        List<String> optionValues = List.of();
        if (!requestedValueIds.isEmpty()) {
            List<ProductOptionValue> optionValueEntities = productOptionValueRepository.findByProductIdAndIdIn(productId, requestedValueIds);
            if (optionValueEntities.size() != requestedValueIds.size()) {
                throw new BusinessException(ErrorCode.OPTION_VALUE_NOT_FOUND);
            }
            for (ProductOptionValue optionValue : optionValueEntities) {
                ProductVariantOption vo = ProductVariantOption.create(variant, optionValue);
                productVariantOptionRepository.save(vo);
            }
            optionValues = optionValueEntities.stream()
                    .map(ProductOptionValue::getValue)
                    .toList();
        }

        return new VariantResponse(variant.getId(), variant.getPrice(), variant.getStock(),
                variant.getStatus(), optionValues);
    }

    @Transactional
    public VariantResponse updateVariant(Long variantId, UpdateVariantRequest request) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));

        variant.updatePriceAndStatus(request.price(), request.status());

        List<String> optionValues = getVariantOptionValues(variantId);
        return new VariantResponse(variant.getId(), variant.getPrice(), variant.getStock(),
                variant.getStatus(), optionValues);
    }

    @Transactional
    public VariantResponse updateStock(Long variantId, UpdateStockRequest request) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));

        variant.updateStock(request.stock());

        List<String> optionValues = getVariantOptionValues(variantId);
        return new VariantResponse(variant.getId(), variant.getPrice(), variant.getStock(),
                variant.getStatus(), optionValues);
    }

    @Transactional
    public VariantResponse decreaseStock(Long variantId, DecreaseStockRequest request) {
        productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));

        int updated = productVariantRepository.decreaseStock(variantId, request.quantity());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.VARIANT_SOLD_OUT);
        }

        // 업데이트 후 최신 상태 반환
        ProductVariant variant = productVariantRepository.findById(variantId).orElseThrow();
        List<String> optionValues = getVariantOptionValues(variantId);
        return new VariantResponse(variant.getId(), variant.getPrice(), variant.getStock(),
                variant.getStatus(), optionValues);
    }

    private void checkDuplicateVariant(Long productId, List<Long> requestedValueIds) {
        Set<Long> requestedSet = Set.copyOf(requestedValueIds);

        // 해당 상품의 모든 variant-optionValue 매핑 조회
        List<ProductVariantOption> existingVOs = productVariantOptionRepository.findByProductId(productId);

        // variant별로 그룹핑
        Map<Long, Set<Long>> variantToValueIds = existingVOs.stream()
                .collect(Collectors.groupingBy(
                        vo -> vo.getVariant().getId(),
                        Collectors.mapping(vo -> vo.getOptionValue().getId(), Collectors.toSet())
                ));

        for (Set<Long> existingSet : variantToValueIds.values()) {
            if (existingSet.equals(requestedSet)) {
                throw new BusinessException(ErrorCode.DUPLICATE_VARIANT);
            }
        }
    }

    private List<String> getVariantOptionValues(Long variantId) {
        return productVariantOptionRepository.findByVariantId(variantId).stream()
                .map(vo -> vo.getOptionValue().getValue())
                .toList();
    }

    private ProductDetailResponse buildProductDetail(Product product) {
        List<ProductOption> options = productOptionRepository.findByProductIdWithValues(product.getId());
        List<ProductDetailResponse.OptionDto> optionDtos = options.stream()
                .map(opt -> new ProductDetailResponse.OptionDto(
                        opt.getName(),
                        opt.getValues().stream().map(ProductOptionValue::getValue).toList()
                ))
                .toList();

        Map<Long, List<String>> variantOptionMap = productVariantOptionRepository.findByProductId(product.getId())
                .stream()
                .collect(Collectors.groupingBy(
                        vo -> vo.getVariant().getId(),
                        Collectors.mapping(vo -> vo.getOptionValue().getValue(), Collectors.toList())
                ));

        List<ProductVariant> variants = productVariantRepository.findByProductId(product.getId());
        List<ProductDetailResponse.VariantDto> variantDtos = variants.stream()
                .map(v -> new ProductDetailResponse.VariantDto(
                        v.getId(), v.getPrice(), v.getStock(), v.getStatus(),
                        variantOptionMap.getOrDefault(v.getId(), List.of())
                ))
                .toList();

        return new ProductDetailResponse(
                product.getId(), product.getName(), product.getDescription(),
                product.getCategory().getId(), product.getStatus(),
                optionDtos, variantDtos
        );
    }

    private ProductResponse toProductResponse(Product product) {
        return new ProductResponse(
                product.getId(), product.getName(), product.getDescription(),
                product.getCategory().getId(), product.getStatus(), product.getCreatedAt()
        );
    }
}