package com.possaas.reporting.web;

import com.possaas.reporting.api.dto.ReportingDtos.DashboardResponse;
import com.possaas.reporting.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('dashboard.view')")
    public DashboardResponse dashboard() {
        return dashboardService.today();
    }
}
