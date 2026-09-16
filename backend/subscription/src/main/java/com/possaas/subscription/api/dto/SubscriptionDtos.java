package com.possaas.subscription.api.dto;

import com.possaas.subscription.domain.BillingCycle;
import com.possaas.subscription.domain.FeatureCode;
import com.possaas.subscription.domain.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    public record PlanFeatureResponse(
            FeatureCode featureCode,
            boolean enabled,
            Integer limitValue
    ) {
    }

    public record PlanResponse(
            UUID id,
            String code,
            String name,
            String description,
            String currency,
            BigDecimal priceMonthly,
            BigDecimal priceYearly,
            short trialDays,
            Integer maxUsers,
            Integer maxOutlets,
            Integer maxItems,
            Integer maxMonthlyBills,
            boolean publicPlan,
            boolean active,
            int displayOrder,
            List<PlanFeatureResponse> features
    ) {
    }

    public record SubscriptionResponse(
            UUID id,
            UUID tenantId,
            UUID planId,
            String planCode,
            String planName,
            SubscriptionStatus status,
            BillingCycle billingCycle,
            String currency,
            BigDecimal unitAmount,
            Instant trialStartedAt,
            Instant trialEndsAt,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant gracePeriodEndsAt,
            boolean cancelAtPeriodEnd,
            Instant cancelledAt,
            String gateway,
            List<PlanFeatureResponse> features
    ) {
    }

    public record CheckoutSessionResponse(
            String sessionId,
            String checkoutUrl,
            String gateway
    ) {
    }

    public record CreateCheckoutRequest(
            BillingCycle billingCycle,
            String successUrl,
            String cancelUrl
    ) {
    }
}
