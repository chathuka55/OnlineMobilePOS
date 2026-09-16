package com.possaas.catalog.service;

import com.possaas.catalog.api.dto.CatalogDtos.CategoryRequest;
import com.possaas.catalog.api.dto.CatalogDtos.CategoryResponse;
import com.possaas.catalog.domain.Category;
import com.possaas.catalog.repository.CategoryRepository;
import com.possaas.common.error.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(boolean activeOnly) {
        List<Category> categories = activeOnly
                ? categoryRepository.findByActiveIsTrueOrderByDisplayOrderAscNameAsc()
                : categoryRepository.findAllByOrderByDisplayOrderAscNameAsc();
        return categories.stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        return CategoryResponse.from(require(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        assertUniqueName(request.name(), null);
        if (request.parentId() != null) {
            require(request.parentId());
        }
        Category category = new Category();
        apply(category, request);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = require(id);
        assertUniqueName(request.name(), id);
        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw ApiException.validation("Category cannot be its own parent");
            }
            require(request.parentId());
        }
        apply(category, request);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void deactivate(UUID id) {
        Category category = require(id);
        category.setActive(false);
        categoryRepository.save(category);
    }

    private void apply(Category category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setParentId(request.parentId());
        if (request.displayOrder() != null) {
            category.setDisplayOrder(request.displayOrder());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
    }

    private Category require(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category", id));
    }

    private void assertUniqueName(String name, UUID excludingId) {
        categoryRepository.findByNameIgnoreCase(name.trim())
                .filter(existing -> excludingId == null || !existing.getId().equals(excludingId))
                .ifPresent(existing -> {
                    throw ApiException.conflict("Category name already exists")
                            .with("name", name.trim());
                });
    }
}
