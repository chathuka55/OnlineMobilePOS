package com.possaas.subscription.domain;

import com.possaas.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One current commercial agreement per tenant. Not under RLS — platform and
 * signup flows read it before a tenant scope exists.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status = SubscriptionStatus.TRIALING;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LKR";

    @Column(name = "unit_amount", nullable = false)
    private BigDecimal unitAmount = BigDecimal.ZERO;

    @Column(name = "trial_started_at")
    private Instant trialStartedAt;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "grace_period_ends_at")
    private Instant gracePeriodEndsAt;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "gateway")
    private String gateway;

    @Column(name = "gateway_customer_ref")
    private String gatewayCustomerRef;

    @Column(name = "gateway_subscription_ref")
    private String gatewaySubscriptionRef;

    public static Subscription startTrial(UUID tenantId, Plan plan, Instant now) {
        Subscription subscription = new Subscription();
        subscription.tenantId = tenantId;
        subscription.planId = plan.getId();
        subscription.status = SubscriptionStatus.TRIALING;
        subscription.billingCycle = BillingCycle.MONTHLY;
        subscription.currency = plan.getCurrency();
        subscription.unitAmount = plan.getPriceMonthly();
        subscription.trialStartedAt = now;
        Instant trialEnd = now.plus(java.time.Duration.ofDays(Math.max(plan.getTrialDays(), 1)));
        subscription.trialEndsAt = trialEnd;
        subscription.currentPeriodStart = now;
        subscription.currentPeriodEnd = trialEnd;
        return subscription;
    }
}
