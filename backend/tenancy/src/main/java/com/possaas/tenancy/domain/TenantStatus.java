package com.possaas.tenancy.domain;

public enum TenantStatus {
    /** Inside the 14-day evaluation window carried over from the desktop product. */
    TRIAL,
    ACTIVE,
    /** Payment failed; still writable during the grace period. */
    PAST_DUE,
    /** Grace period elapsed. Read-only. */
    SUSPENDED,
    /** Closed account. Login is refused. */
    CANCELLED
}
