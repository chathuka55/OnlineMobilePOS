package com.possaas.subscription.service;

import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.tenant.TenantContext;
import com.possaas.subscription.api.dto.SubscriptionDtos.PlanFeatureResponse;
import com.possaas.subscription.api.dto.SubscriptionDtos.PlanResponse;
import com.possaas.subscription.api.dto.SubscriptionDtos.SubscriptionResponse;
import com.possaas.subscription.domain.Plan;
import com.possaas.subscription.domain.PlanFeature;
import com.possaas.subscription.domain.Subscription;
import com.possaas.subscription.repository.PlanFeatureRepository;
import com.possaas.subscription.repository.PlanRepository;
import com.possaas.subscription.repository.SubscriptionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionQueryService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;

    public SubscriptionQueryService(SubscriptionRepository subscriptionRepository,
                                    PlanRepository planRepository,
                                    PlanFeatureRepository planFeatureRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.planFeatureRepository = planFeatureRepository;
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse current() {
        UUID tenantId = TenantContext.requireTenantId();
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.SUBSCRIPTION_INACTIVE,
                        "No subscription found for this workspace"));
        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> ApiException.notFound("Plan", subscription.getPlanId()));
        return toSubscriptionResponse(subscription, plan);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> publicPlans() {
        return planRepository.findByPublicPlanIsTrueAndActiveIsTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toPlanResponse)
                .toList();
    }

    public PlanResponse toPlanResponse(Plan plan) {
        List<PlanFeatureResponse> features = planFeatureRepository.findByPlanId(plan.getId())
                .stream()
                .map(this::toFeatureResponse)
                .toList();
        return new PlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getCurrency(),
                plan.getPriceMonthly(),
                plan.getPriceYearly(),
                plan.getTrialDays(),
                plan.getMaxUsers(),
                plan.getMaxOutlets(),
                plan.getMaxItems(),
                plan.getMaxMonthlyBills(),
                plan.isPublicPlan(),
                plan.isActive(),
                plan.getDisplayOrder(),
                features);
    }

    private SubscriptionResponse toSubscriptionResponse(Subscription subscription, Plan plan) {
        List<PlanFeatureResponse> features = planFeatureRepository.findByPlanId(plan.getId())
                .stream()
                .map(this::toFeatureResponse)
                .toList();
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getTenantId(),
                subscription.getPlanId(),
                plan.getCode(),
                plan.getName(),
                subscription.getStatus(),
                subscription.getBillingCycle(),
                subscription.getCurrency(),
                subscription.getUnitAmount(),
                subscription.getTrialStartedAt(),
                subscription.getTrialEndsAt(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getGracePeriodEndsAt(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getCancelledAt(),
                subscription.getGateway(),
                features);
    }

    private PlanFeatureResponse toFeatureResponse(PlanFeature feature) {
        return new PlanFeatureResponse(
                feature.getFeatureCode(),
                feature.isEnabled(),
                feature.getLimitValue());
    }
}
