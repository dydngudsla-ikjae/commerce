package com.commerce.product.service;

import com.commerce.global.exception.BusinessException;
import com.commerce.global.exception.ErrorCode;
import com.commerce.product.domain.Category;
import com.commerce.product.dto.CategoryResponse;
import com.commerce.product.dto.CreateCategoryRequest;
import com.commerce.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        Category category;
        if (request.parentId() == null) {
            category = Category.createRoot(request.name());
        } else {
            Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
            category = Category.createChild(request.name(), parent);
        }
        categoryRepository.save(category);
        return new CategoryResponse(category.getId(), category.getName(), List.of());
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories() {
        List<Category> all = categoryRepository.findAllWithParent();

        // 부모가 없는 루트 카테고리 기준으로 트리 구성
        Map<Long, List<Category>> childrenByParentId = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));

        List<Category> roots = all.stream()
                .filter(c -> c.getParentId() == null)
                .toList();

        return roots.stream()
                .map(root -> buildTree(root, childrenByParentId))
                .toList();
    }

    private CategoryResponse buildTree(Category category, Map<Long, List<Category>> childrenByParentId) {
        List<Category> children = childrenByParentId.getOrDefault(category.getId(), List.of());
        List<CategoryResponse> childResponses = children.stream()
                .map(child -> buildTree(child, childrenByParentId))
                .toList();
        return new CategoryResponse(category.getId(), category.getName(), childResponses);
    }
}