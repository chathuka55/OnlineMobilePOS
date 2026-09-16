package com.possaas.catalog.domain;

/** Lifecycle of a physical serialised unit. Matches {@code item_serials_status_check}. */
public enum SerialStatus {
    IN_STOCK,
    RESERVED,
    SOLD,
    RETURNED,
    DEFECTIVE,
    WRITTEN_OFF
}
