package com.possaas.catalog.api;

import com.possaas.catalog.api.dto.CatalogDtos.ItemRequest;
import com.possaas.catalog.api.dto.CatalogDtos.ItemResponse;
import com.possaas.catalog.api.dto.CatalogDtos.StockAdjustRequest;
import com.possaas.catalog.service.ItemService;
import com.possaas.common.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('item.view')")
    public PageResponse<ItemResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @PageableDefault(size = 50, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return PageResponse.of(itemService.search(q, activeOnly, pageable));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('item.view')")
    public PageResponse<ItemResponse> lowStock(
            @PageableDefault(size = 50, sort = "name") Pageable pageable) {
        return PageResponse.of(itemService.lowStock(pageable));
    }

    @GetMapping("/by-barcode/{barcode}")
    @PreAuthorize("hasAuthority('item.view')")
    public ItemResponse byBarcode(@PathVariable String barcode) {
        return itemService.findByBarcode(barcode);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('item.view')")
    public ItemResponse get(@PathVariable UUID id) {
        return itemService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('item.create')")
    public ItemResponse create(@Valid @RequestBody ItemRequest request) {
        return itemService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('item.edit')")
    public ItemResponse update(@PathVariable UUID id, @Valid @RequestBody ItemRequest request) {
        return itemService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('item.delete')")
    public void delete(@PathVariable UUID id) {
        itemService.softDelete(id);
    }

    @PostMapping("/{id}/adjust-stock")
    @PreAuthorize("hasAuthority('stock.adjust')")
    public ItemResponse adjustStock(@PathVariable UUID id,
                                    @Valid @RequestBody StockAdjustRequest request) {
        return itemService.adjustStock(id, request);
    }
}
