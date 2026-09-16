package com.possaas.platform.web;

import com.possaas.platform.api.dto.PlatformDtos.PlanFeatureRequest;
import com.possaas.platform.api.dto.PlatformDtos.UpsertPlanRequest;
import com.possaas.platform.service.PlatformPlanService;
import com.possaas.subscription.api.dto.SubscriptionDtos.PlanResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/plans")
public class PlatformPlanController {

    private final PlatformPlanService platformPlanService;

    public PlatformPlanController(PlatformPlanService platformPlanService) {
        this.platformPlanService = platformPlanService;
    }

    @GetMapping
    public List<PlanResponse> list() {
        return platformPlanService.listAll();
    }

    @GetMapping("/{planId}")
    public PlanResponse get(@PathVariable UUID planId) {
        return platformPlanService.get(planId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse create(@Valid @RequestBody UpsertPlanRequest request) {
        return platformPlanService.create(request);
    }

    @PutMapping("/{planId}")
    public PlanResponse update(@PathVariable UUID planId,
                               @Valid @RequestBody UpsertPlanRequest request) {
        return platformPlanService.update(planId, request);
    }

    @PutMapping("/{planId}/features")
    public PlanResponse replaceFeatures(@PathVariable UUID planId,
                                        @RequestBody List<PlanFeatureRequest> features) {
        return platformPlanService.replaceFeatures(planId, features);
    }

    @DeleteMapping("/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID planId) {
        platformPlanService.delete(planId);
    }
}
