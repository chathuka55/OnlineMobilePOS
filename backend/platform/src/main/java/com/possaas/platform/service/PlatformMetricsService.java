package com.possaas.platform.service;

import com.possaas.platform.api.dto.PlatformDtos.PlatformMetricsResponse;
import com.possaas.subscription.domain.BillingCycle;
import com.possaas.subscription.domain.Subscription;
import com.possaas.subscription.domain.SubscriptionStatus;
import com.possaas.subscription.repository.PlanRepository;
import com.possaas.subscription.repository.SubscriptionRepository;
import com.possaas.tenancy.domain.TenantStatus;
import com.possaas.tenancy.repository.TenantRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformMetricsService {

    private static final Set<SubscriptionStatus> MRR_STATUSES = EnumSet.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.GRACE);

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    public PlatformMetricsService(TenantRepository tenantRepository,
                                  SubscriptionRepository subscriptionRepository,
                                  PlanRepository planRepository) {
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    @Transactional(readOnly = true)
    public PlatformMetricsResponse metrics() {
        long total = tenantRepository.count();
        long active = tenantRepository.countByStatus(TenantStatus.ACTIVE);
        long trial = tenantRepository.countByStatus(TenantStatus.TRIAL);
        long suspended = tenantRepository.countByStatus(TenantStatus.SUSPENDED);
        long pastDue = subscriptionRepository.countByStatus(SubscriptionStatus.PAST_DUE);
        long grace = subscriptionRepository.countByStatus(SubscriptionStatus.GRACE);

        BigDecimal mrr = BigDecimal.ZERO;
        List<Subscription> subscriptions = subscriptionRepository.findAll();
        for (Subscription subscription : subscriptions) {
            if (!MRR_STATUSES.contains(subscription.getStatus())) {
                continue;
            }
            BigDecimal monthly = subscription.getUnitAmount() == null
                    ? BigDecimal.ZERO
                    : subscription.getUnitAmount();
            if (subscription.getBillingCycle() == BillingCycle.YEARLY) {
                monthly = monthly.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            }
            mrr = mrr.add(monthly);
        }

        // Stub churn: suspended / (active + suspended) when denominator > 0.
        double churnStub = 0.0;
        long churnBase = active + suspended;
        if (churnBase > 0) {
            churnStub = (double) suspended / (double) churnBase;
        }

        String currency = planRepository.findByCodeIgnoreCaseAndActiveIsTrue("STARTER")
                .map(p -> p.getCurrency())
                .orElse("LKR");

        return new PlatformMetricsResponse(
                total,
                active,
                trial,
                suspended,
                pastDue,
                grace,
                mrr.setScale(2, RoundingMode.HALF_UP),
                mrr.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP),
                Math.round(churnStub * 10_000.0) / 10_000.0,
                currency);
    }
}
