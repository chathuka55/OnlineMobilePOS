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
    COMPLETED,
    DELIVERED,
    CANCELLED,
    IRREPARABLE
}
