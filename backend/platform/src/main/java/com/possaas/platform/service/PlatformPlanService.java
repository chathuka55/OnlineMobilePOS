package com.possaas.platform.service;

import com.possaas.common.error.ApiException;
import com.possaas.subscription.api.dto.SubscriptionDtos.PlanResponse;
import com.possaas.subscription.domain.Plan;
import com.possaas.subscription.domain.PlanFeature;
import com.possaas.subscription.repository.PlanFeatureRepository;
import com.possaas.subscription.repository.PlanRepository;
import com.possaas.subscription.service.SubscriptionQueryService;
import com.possaas.platform.api.dto.PlatformDtos.PlanFeatureRequest;
import com.possaas.platform.api.dto.PlatformDtos.UpsertPlanRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformPlanService {

    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final SubscriptionQueryService subscriptionQueryService;

    public PlatformPlanService(PlanRepository planRepository,
                               PlanFeatureRepository planFeatureRepository,
                               SubscriptionQueryService subscriptionQueryService) {
        this.planRepository = planRepository;
        this.planFeatureRepository = planFeatureRepository;
        this.subscriptionQueryService = subscriptionQueryService;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listAll() {
        return planRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(subscriptionQueryService::toPlanResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse get(UUID planId) {
        return subscriptionQueryService.toPlanResponse(requirePlan(planId));
    }

    @Transactional
    public PlanResponse create(UpsertPlanRequest request) {
        planRepository.findByCodeIgnoreCase(request.code()).ifPresent(existing -> {
            throw ApiException.conflict("Plan code already exists: " + request.code());
        });
        Plan plan = new Plan();
        apply(plan, request);
        planRepository.save(plan);
        doReplaceFeatures(plan.getId(), request.features() == null ? List.of() : request.features());
        return subscriptionQueryService.toPlanResponse(plan);
    }

    @Transactional
    public PlanResponse update(UUID planId, UpsertPlanRequest request) {
        Plan plan = requirePlan(planId);
        planRepository.findByCodeIgnoreCase(request.code()).ifPresent(existing -> {
            if (!existing.getId().equals(planId)) {
                throw ApiException.conflict("Plan code already exists: " + request.code());
            }
        });
        apply(plan, request);
        planRepository.save(plan);
        if (request.features() != null) {
            doReplaceFeatures(plan.getId(), request.features());
        }
        return subscriptionQueryService.toPlanResponse(plan);
    }

    @Transactional
    public void delete(UUID planId) {
        Plan plan = requirePlan(planId);
        plan.setActive(false);
        plan.setPublicPlan(false);
        planRepository.save(plan);
    }

    @Transactional
    public PlanResponse replaceFeatures(UUID planId, List<PlanFeatureRequest> features) {
        requirePlan(planId);
        doReplaceFeatures(planId, features == null ? List.of() : features);
        return subscriptionQueryService.toPlanResponse(requirePlan(planId));
    }

    private void doReplaceFeatures(UUID planId, List<PlanFeatureRequest> features) {
        planFeatureRepository.deleteByPlanId(planId);
        planFeatureRepository.flush();
        for (PlanFeatureRequest featureRequest : features) {
            if (featureRequest == null || featureRequest.featureCode() == null) {
                continue;
            }
            PlanFeature feature = PlanFeature.of(planId, featureRequest.featureCode());
            feature.setEnabled(featureRequest.enabled() == null || featureRequest.enabled());
            feature.setLimitValue(featureRequest.limitValue());
            planFeatureRepository.save(feature);
        }
    }

    private void apply(Plan plan, UpsertPlanRequest request) {
        plan.setCode(request.code().trim().toUpperCase());
        plan.setName(request.name().trim());
        plan.setDescription(request.description());
        if (request.currency() != null) {
            plan.setCurrency(request.currency().toUpperCase());
        }
        plan.setPriceMonthly(nullToZero(request.priceMonthly()));
        plan.setPriceYearly(nullToZero(request.priceYearly()));
        if (request.trialDays() != null) {
            plan.setTrialDays(request.trialDays());
        }
        plan.setMaxUsers(request.maxUsers());
        plan.setMaxOutlets(request.maxOutlets());
        plan.setMaxItems(request.maxItems());
        plan.setMaxMonthlyBills(request.maxMonthlyBills());
        if (request.publicPlan() != null) {
            plan.setPublicPlan(request.publicPlan());
        }
        if (request.active() != null) {
            plan.setActive(request.active());
        }
        if (request.displayOrder() != null) {
            plan.setDisplayOrder(request.displayOrder());
        }
    }

    private Plan requirePlan(UUID planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> ApiException.notFound("Plan", planId));
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
