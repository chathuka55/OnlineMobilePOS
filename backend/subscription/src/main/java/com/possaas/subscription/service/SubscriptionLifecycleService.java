package com.possaas.subscription.service;

import com.possaas.subscription.config.SubscriptionProperties;
import com.possaas.subscription.domain.Plan;
import com.possaas.subscription.domain.Subscription;
import com.possaas.subscription.domain.SubscriptionStatus;
import com.possaas.subscription.repository.PlanRepository;
import com.possaas.subscription.repository.SubscriptionRepository;
import com.possaas.tenancy.domain.Tenant;
import com.possaas.tenancy.domain.TenantStatus;
import com.possaas.tenancy.repository.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Advances subscriptions through trial → active → past_due → grace → read-only lockout.
 *
 * <p>Payment success paths call {@link #activate} / {@link #markPastDue}; the nightly
 * scheduler closes expired trials and grace windows using {@code pos.subscription.*} days.
 */
@Service
public class SubscriptionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionLifecycleService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionProperties properties;

    public SubscriptionLifecycleService(SubscriptionRepository subscriptionRepository,
                                        PlanRepository planRepository,
                                        TenantRepository tenantRepository,
                                        SubscriptionProperties properties) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.tenantRepository = tenantRepository;
        this.properties = properties;
    }

    /** Trial or checkout succeeded — open a paid period. */
    @Transactional
    public Subscription activate(Subscription subscription, Instant periodEnd) {
        Instant now = Instant.now();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(periodEnd);
        subscription.setGracePeriodEndsAt(null);
        subscriptionRepository.save(subscription);
        markTenantWritable(subscription.getTenantId(), TenantStatus.ACTIVE);
        log.info("Activated subscription {} for tenant {}",
                subscription.getId(), subscription.getTenantId());
        return subscription;
    }

    /** Payment failed or period lapsed without renewal. */
    @Transactional
    public Subscription markPastDue(Subscription subscription) {
        Instant now = Instant.now();
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        subscription.setGracePeriodEndsAt(null);
        subscriptionRepository.save(subscription);
        markTenantWritable(subscription.getTenantId(), TenantStatus.PAST_DUE);
        log.info("Subscription {} for tenant {} is PAST_DUE at {}",
                subscription.getId(), subscription.getTenantId(), now);
        return subscription;
    }

    /** Enter the configured grace window; tenant remains writable. */
    @Transactional
    public Subscription enterGrace(Subscription subscription) {
        Instant now = Instant.now();
        Instant graceEnd = now.plus(Duration.ofDays(properties.gracePeriodDays()));
        subscription.setStatus(SubscriptionStatus.GRACE);
        subscription.setGracePeriodEndsAt(graceEnd);
        subscriptionRepository.save(subscription);
        markTenantWritable(subscription.getTenantId(), TenantStatus.PAST_DUE);
        log.info("Subscription {} entered GRACE until {}", subscription.getId(), graceEnd);
        return subscription;
    }

    /**
     * Grace elapsed (or FREE plan lockout). Tenant becomes read-only; subscription
     * moves to SUSPENDED and is switched onto the FREE catalogue plan when present.
     */
    @Transactional
    public Subscription lockReadOnly(Subscription subscription) {
        Instant now = Instant.now();
        subscription.setStatus(SubscriptionStatus.SUSPENDED);
        subscription.setCancelledAt(now);
        planRepository.findByCodeIgnoreCaseAndActiveIsTrue("FREE").ifPresent(free -> {
            subscription.setPlanId(free.getId());
            subscription.setUnitAmount(free.getPriceMonthly());
            subscription.setCurrency(free.getCurrency());
        });
        subscriptionRepository.save(subscription);

        tenantRepository.findById(subscription.getTenantId()).ifPresent(tenant -> {
            tenant.suspend("Subscription grace period expired");
            tenantRepository.save(tenant);
        });
        log.info("Locked tenant {} to read-only (subscription {})",
                subscription.getTenantId(), subscription.getId());
        return subscription;
    }

    @Scheduled(cron = "0 15 2 * * *")
    @Transactional
    public void processLifecycleTransitions() {
        Instant now = Instant.now();
        int moved = 0;

        List<Subscription> expiredTrials = subscriptionRepository.findExpiredTrials(now);
        for (Subscription subscription : expiredTrials) {
            markPastDue(subscription);
            enterGrace(subscription);
            moved++;
        }

        List<Subscription> lapsed = subscriptionRepository.findLapsedActive(now);
        for (Subscription subscription : lapsed) {
            markPastDue(subscription);
            enterGrace(subscription);
            moved++;
        }

        List<Subscription> pastDue = subscriptionRepository.findPastDue();
        for (Subscription subscription : pastDue) {
            // Past-due rows that have not yet entered grace get a grace window.
            if (subscription.getGracePeriodEndsAt() == null) {
                enterGrace(subscription);
                moved++;
            }
        }

        List<Subscription> expiredGrace = subscriptionRepository.findExpiredGrace(now);
        for (Subscription subscription : expiredGrace) {
            lockReadOnly(subscription);
            moved++;
        }

        if (moved > 0) {
            log.info("Subscription lifecycle job advanced {} subscription(s)", moved);
        }
    }

    private void markTenantWritable(java.util.UUID tenantId, TenantStatus status) {
        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            if (tenant.getDeletedAt() != null) {
                return;
            }
            tenant.setStatus(status);
            tenant.setSuspendedAt(null);
            tenant.setSuspensionReason(null);
            if (tenant.getOnboardedAt() == null && status == TenantStatus.ACTIVE) {
                tenant.setOnboardedAt(Instant.now());
            }
            tenantRepository.save(tenant);
        });
    }

    @Transactional(readOnly = true)
    public Plan requirePlan(Subscription subscription) {
        return planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException(
                        "Plan missing for subscription " + subscription.getId()));
    }

    /** Used by tests / admin tooling to inspect tenant writable state. */
    @Transactional(readOnly = true)
    public boolean isWritable(Tenant tenant) {
        return tenant.canWrite();
    }
}
