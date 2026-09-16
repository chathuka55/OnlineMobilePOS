package com.possaas.subscription.service;

import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.tenant.TenantContext;
import com.possaas.subscription.domain.FeatureCode;
import com.possaas.subscription.domain.Plan;
import com.possaas.subscription.domain.Subscription;
import com.possaas.subscription.domain.SubscriptionStatus;
import com.possaas.subscription.repository.PlanFeatureRepository;
import com.possaas.subscription.repository.PlanRepository;
import com.possaas.subscription.repository.SubscriptionRepository;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves plan entitlements and quotas for the current tenant.
 */
@Service
public class EntitlementService {

    private static final Set<SubscriptionStatus> ENTITLED_STATUSES = EnumSet.of(
            SubscriptionStatus.TRIALING,
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.GRACE);

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final JdbcTemplate jdbcTemplate;

    public EntitlementService(SubscriptionRepository subscriptionRepository,
                              PlanRepository planRepository,
                              PlanFeatureRepository planFeatureRepository,
                              JdbcTemplate jdbcTemplate) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.planFeatureRepository = planFeatureRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public boolean hasFeature(String code) {
        FeatureCode feature = parseFeature(code);
        return hasFeature(feature);
    }

    @Transactional(readOnly = true)
    public boolean hasFeature(FeatureCode feature) {
        Subscription subscription = requireCurrentSubscription();
        if (!ENTITLED_STATUSES.contains(subscription.getStatus())) {
            return false;
        }
        return planFeatureRepository.existsByPlanIdAndFeatureCodeAndEnabledIsTrue(
                subscription.getPlanId(), feature);
    }

    @Transactional(readOnly = true)
    public void assertFeature(String code) {
        FeatureCode feature = parseFeature(code);
        if (!hasFeature(feature)) {
            throw new ApiException(ErrorCode.FEATURE_NOT_IN_PLAN,
                    "Your plan does not include " + feature.name())
                    .with("feature", feature.name());
        }
    }

    @Transactional(readOnly = true)
    public void checkSeatQuota() {
        Plan plan = requireCurrentPlan();
        if (plan.getMaxUsers() == null) {
            return;
        }
        long seats = countActiveUsers(TenantContext.requireTenantId());
        if (seats >= plan.getMaxUsers()) {
            throw new ApiException(ErrorCode.QUOTA_EXCEEDED,
                    "User seat quota reached for this plan")
                    .with("metric", "USERS")
                    .with("limit", plan.getMaxUsers())
                    .with("current", seats);
        }
    }

    @Transactional(readOnly = true)
    public void checkOutletQuota() {
        Plan plan = requireCurrentPlan();
        if (plan.getMaxOutlets() == null) {
            return;
        }
        long outlets = countActiveOutlets(TenantContext.requireTenantId());
        if (outlets >= plan.getMaxOutlets()) {
            throw new ApiException(ErrorCode.QUOTA_EXCEEDED,
                    "Outlet quota reached for this plan")
                    .with("metric", "OUTLETS")
                    .with("limit", plan.getMaxOutlets())
                    .with("current", outlets);
        }
    }

    @Transactional(readOnly = true)
    public Plan requireCurrentPlan() {
        Subscription subscription = requireCurrentSubscription();
        return planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> ApiException.notFound("Plan", subscription.getPlanId()));
    }

    @Transactional(readOnly = true)
    public Subscription requireCurrentSubscription() {
        UUID tenantId = TenantContext.requireTenantId();
        return subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.SUBSCRIPTION_INACTIVE,
                        "No subscription found for this workspace"));
    }

    private long countActiveUsers(UUID tenantId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM users
                         WHERE tenant_id = ?
                           AND deleted_at IS NULL
                           AND status = 'ACTIVE'
                        """,
                Long.class,
                tenantId);
        return count == null ? 0L : count;
    }

    private long countActiveOutlets(UUID tenantId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM outlets
                         WHERE tenant_id = ?
                           AND is_active = true
                        """,
                Long.class,
                tenantId);
        return count == null ? 0L : count;
    }

    private static FeatureCode parseFeature(String code) {
        try {
            return FeatureCode.valueOf(code.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw ApiException.validation("Unknown feature code: " + code);
        }
    }
}
