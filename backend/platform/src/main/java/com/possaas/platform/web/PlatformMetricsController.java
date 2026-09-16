package com.possaas.platform.web;

import com.possaas.platform.api.dto.PlatformDtos.PlatformMetricsResponse;
import com.possaas.platform.service.PlatformMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/metrics")
public class PlatformMetricsController {

    private final PlatformMetricsService platformMetricsService;

    public PlatformMetricsController(PlatformMetricsService platformMetricsService) {
        this.platformMetricsService = platformMetricsService;
    }

    @GetMapping
    public PlatformMetricsResponse metrics() {
        return platformMetricsService.metrics();
    }
}
