package com.possaas.catalog.api;

import com.possaas.catalog.api.dto.CatalogDtos.GrnCreateRequest;
import com.possaas.catalog.api.dto.CatalogDtos.GrnResponse;
import com.possaas.catalog.service.GrnService;
import com.possaas.common.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grns")
public class GrnController {

    private final GrnService grnService;

    public GrnController(GrnService grnService) {
        this.grnService = grnService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('grn.view')")
    public PageResponse<GrnResponse> list(
            @PageableDefault(size = 50, sort = "receivedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(grnService.list(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('grn.view')")
    public GrnResponse get(@PathVariable UUID id) {
        return grnService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('grn.create') and hasAuthority('grn.post')")
    public GrnResponse create(@Valid @RequestBody GrnCreateRequest request) {
        return grnService.createAndPost(request);
    }
}
