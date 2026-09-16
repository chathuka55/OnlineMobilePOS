package com.possaas.platform.api.dto;

import com.possaas.subscription.domain.FeatureCode;
import com.possaas.subscription.domain.SubscriptionStatus;
import com.possaas.tenancy.domain.TenantStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PlatformDtos {

    private PlatformDtos() {
    }

    public record TenantSummaryResponse(
            UUID id,
            String slug,
            String businessName,
            String contactEmail,
            TenantStatus status,
            String defaultCurrency,
            Instant createdAt,
            Instant suspendedAt,
            SubscriptionStatus subscriptionStatus,
            String planCode
    ) {
    }

    public record TenantDetailResponse(
            UUID id,
            String slug,
            String businessName,
            String legalName,
            String taxIdentifier,
            TenantStatus status,
            String defaultCurrency,
            String defaultLocale,
            String timeZone,
            String contactEmail,
            String contactPhone,
            Instant onboardedAt,
            Instant suspendedAt,
            String suspensionReason,
            Instant createdAt,
            SubscriptionStatus subscriptionStatus,
            String planCode,
            String planName,
            Instant trialEndsAt,
            Instant currentPeriodEnd,
            Instant gracePeriodEndsAt
    ) {
    }

    public record SuspendTenantRequest(
            @Size(max = 400) String reason
    ) {
    }

    public record ImpersonateResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            Instant expiresAt,
            UUID tenantId,
            String tenantSlug,
            UUID actingUserId,
            UUID impersonatorId
    ) {
        public ImpersonateResponse(String accessToken, long expiresIn, Instant expiresAt,
                                   UUID tenantId, String tenantSlug,
                                   UUID actingUserId, UUID impersonatorId) {
            this(accessToken, "Bearer", expiresIn, expiresAt,
                    tenantId, tenantSlug, actingUserId, impersonatorId);
        }
    }

    public record PlanFeatureRequest(
            @NotNull FeatureCode featureCode,
            Boolean enabled,
            Integer limitValue
    ) {
    }

    public record UpsertPlanRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            String description,
            @Size(min = 3, max = 3) String currency,
            BigDecimal priceMonthly,
            BigDecimal priceYearly,
            Short trialDays,
            Integer maxUsers,
            Integer maxOutlets,
            Integer maxItems,
            Integer maxMonthlyBills,
            Boolean publicPlan,
            Boolean active,
            Integer displayOrder,
            List<PlanFeatureRequest> features
    ) {
    }

    public record PlatformMetricsResponse(
            long totalTenants,
            long activeTenants,
            long trialTenants,
            long suspendedTenants,
            long pastDueSubscriptions,
            long graceSubscriptions,
            BigDecimal mrr,
            BigDecimal arr,
            double churnRateStub,
            String currency
    ) {
    }
}
