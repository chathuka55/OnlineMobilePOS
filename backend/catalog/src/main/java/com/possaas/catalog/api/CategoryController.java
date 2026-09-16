package com.possaas.catalog.api;

import com.possaas.catalog.api.dto.CatalogDtos.CategoryRequest;
import com.possaas.catalog.api.dto.CatalogDtos.CategoryResponse;
import com.possaas.catalog.service.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('item.view')")
    public List<CategoryResponse> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return categoryService.list(activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('item.view')")
    public CategoryResponse get(@PathVariable UUID id) {
        return categoryService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('item.create')")
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('item.edit')")
    public CategoryResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('item.edit')")
    public void deactivate(@PathVariable UUID id) {
        categoryService.deactivate(id);
    }
}
