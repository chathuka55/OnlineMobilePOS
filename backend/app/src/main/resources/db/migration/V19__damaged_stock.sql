-- ============================================================================
-- V19  Damaged-stock tracking, per item and (via items.supplier_id) per supplier.
--
-- Previously the only way to record damage was WRITE_OFF, which just removed
-- units from quantity_on_hand with no record of "how much of this item, from
-- this supplier, has been damaged" - a shop couldn't report or reclaim on it.
-- quantity_damaged is a separate bucket (not part of quantity_on_hand) so
-- damaged units are visibly held aside rather than silently vanishing.
-- ============================================================================

ALTER TABLE items ADD COLUMN quantity_damaged numeric(14, 3) NOT NULL DEFAULT 0;
ALTER TABLE items ADD CONSTRAINT items_damaged_check CHECK (quantity_damaged >= 0);

ALTER TABLE stock_movements DROP CONSTRAINT stock_movements_type_check;
ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_type_check CHECK (
    movement_type IN (
        'GRN', 'SALE', 'SALE_RETURN', 'REPAIR_PART', 'REPAIR_PART_RETURN',
        'WHOLESALE_SALE', 'WHOLESALE_RETURN', 'SUPPLIER_RETURN',
        'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'OPENING_BALANCE',
        'TRANSFER_IN', 'TRANSFER_OUT', 'WRITE_OFF', 'DAMAGED', 'DAMAGED_RESTORED'
    )
);

-- Per-supplier damaged-stock report, joined at query time rather than materialised.
CREATE INDEX items_tenant_damaged_idx ON items (tenant_id, supplier_id)
    WHERE quantity_damaged > 0;
