package com.possaas.repairs.domain;

/**
 * Matches {@code repair_orders_status_check}. Desktop used inconsistent labels
 * ({@code InProgress} vs {@code In Progress}); these canonical values are the contract.
 */
public enum RepairOrderStatus {
    RECEIVED,
    DIAGNOSING,
    AWAITING_APPROVAL,
    AWAITING_PARTS,
    IN_PROGRESS,
    /** Post-repair inspection. A job cannot reach the customer without passing it. */
    QC,
    COMPLETED,
    DELIVERED,
    CANCELLED,
    IRREPARABLE
}
