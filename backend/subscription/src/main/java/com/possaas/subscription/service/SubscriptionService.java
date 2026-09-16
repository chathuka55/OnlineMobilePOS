package com.possaas.subscription.service;

import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.subscription.domain.Plan;
import com.possaas.subscription.domain.Subscription;
import com.possaas.subscription.repository.PlanRepository;
import com.possaas.subscription.repository.SubscriptionRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal commercial lifecycle used at tenant signup. Billing webhooks and plan
 * changes land in later work.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    /** Seeded in V13 — the public trial entry plan. */
    public static final String DEFAULT_TRIAL_PLAN_CODE = "STARTER";

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               PlanRepository planRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
    }

    /**
     * Creates a {@code TRIALING} subscription on the Starter plan when the tenant
     * does not already have one.
     */
    @Transactional
    public Subscription startTrial(UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> createTrial(tenantId));
    }

    private Subscription createTrial(UUID tenantId) {
        Plan plan = planRepository.findByCodeIgnoreCaseAndActiveIsTrue(DEFAULT_TRIAL_PLAN_CODE)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "Trial plan '" + DEFAULT_TRIAL_PLAN_CODE + "' is not seeded"));

        Subscription subscription = Subscription.startTrial(tenantId, plan, Instant.now());
        subscriptionRepository.save(subscription);
        log.info("Started {} trial for tenant {} on plan {}",
                plan.getTrialDays(), tenantId, plan.getCode());
        return subscription;
    }
}
