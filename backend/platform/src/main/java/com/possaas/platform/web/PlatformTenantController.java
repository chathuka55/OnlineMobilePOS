package com.possaas.platform.web;

import com.possaas.common.api.PageResponse;
import com.possaas.platform.api.dto.PlatformDtos.ImpersonateResponse;
import com.possaas.platform.api.dto.PlatformDtos.SuspendTenantRequest;
import com.possaas.platform.api.dto.PlatformDtos.TenantDetailResponse;
import com.possaas.platform.api.dto.PlatformDtos.TenantSummaryResponse;
import com.possaas.platform.service.PlatformTenantService;
import com.possaas.tenancy.domain.TenantStatus;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/tenants")
public class PlatformTenantController {

    private final PlatformTenantService platformTenantService;

    public PlatformTenantController(PlatformTenantService platformTenantService) {
        this.platformTenantService = platformTenantService;
    }

    @GetMapping
    public PageResponse<TenantSummaryResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TenantStatus status,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return platformTenantService.list(q, status, pageable);
    }

    @GetMapping("/{tenantId}")
    public TenantDetailResponse get(@PathVariable UUID tenantId) {
        return platformTenantService.get(tenantId);
    }

    @PostMapping("/{tenantId}/suspend")
    public TenantDetailResponse suspend(@PathVariable UUID tenantId,
                                        @RequestBody(required = false) SuspendTenantRequest request) {
        return platformTenantService.suspend(tenantId,
                request == null ? new SuspendTenantRequest(null) : request);
    }

    @PostMapping("/{tenantId}/unsuspend")
    public TenantDetailResponse unsuspend(@PathVariable UUID tenantId) {
        return platformTenantService.unsuspend(tenantId);
    }

    @PostMapping("/{tenantId}/impersonate")
    public ImpersonateResponse impersonate(@PathVariable UUID tenantId) {
        return platformTenantService.impersonate(tenantId);
    }
}
