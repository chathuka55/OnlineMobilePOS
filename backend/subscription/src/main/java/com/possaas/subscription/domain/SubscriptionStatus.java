package com.possaas.subscription.domain;

public enum SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    GRACE,
    SUSPENDED,
    CANCELLED,
    EXPIRED
}
