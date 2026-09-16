package com.possaas.subscription.domain;

/** Metrics tracked in {@code usage_counters}. Must match V3 CHECK constraint. */
public enum UsageMetric {
    BILLS,
    ITEMS,
    USERS,
    OUTLETS,
    API_CALLS,
    STORAGE_BYTES
}
