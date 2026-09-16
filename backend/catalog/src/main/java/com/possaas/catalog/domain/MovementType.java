package com.possaas.catalog.domain;

/** Append-only stock ledger movement kinds. Matches {@code stock_movements_type_check}. */
public enum MovementType {
    GRN,
    SALE,
    SALE_RETURN,
    REPAIR_PART,
    REPAIR_PART_RETURN,
    WHOLESALE_SALE,
    WHOLESALE_RETURN,
    SUPPLIER_RETURN,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    OPENING_BALANCE,
    TRANSFER_IN,
    TRANSFER_OUT,
    WRITE_OFF
}
