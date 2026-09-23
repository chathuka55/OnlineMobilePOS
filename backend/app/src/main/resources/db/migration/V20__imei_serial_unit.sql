-- ============================================================================
-- V20  Per-unit IMEI ledger.
--
-- A phone shop identifies a unit by IMEI, not by our internal serial number,
-- and a dual-SIM handset carries two of them. Until now item_serials held a
-- single opaque serial_number, so "which invoice sold IMEI 35693...?" could
-- only be answered by a text match that would also hit a laptop's service tag.
--
-- Condition/grade/battery_health are what make a used or trade-in unit
-- priceable: the same model at grade A vs grade D is a different product to a
-- buyer, and battery health under 80% is the standard markdown trigger.
-- ============================================================================

ALTER TABLE item_serials ADD COLUMN imei1          varchar(20);
ALTER TABLE item_serials ADD COLUMN imei2          varchar(20);
ALTER TABLE item_serials ADD COLUMN unit_condition varchar(12) NOT NULL DEFAULT 'NEW';
ALTER TABLE item_serials ADD COLUMN grade          varchar(1);
ALTER TABLE item_serials ADD COLUMN battery_health smallint;

ALTER TABLE item_serials ADD CONSTRAINT item_serials_condition_check CHECK (
    unit_condition IN ('NEW', 'OPEN_BOX', 'USED', 'REFURB')
);
ALTER TABLE item_serials ADD CONSTRAINT item_serials_grade_check CHECK (
    grade IS NULL OR grade IN ('A', 'B', 'C', 'D')
);
ALTER TABLE item_serials ADD CONSTRAINT item_serials_battery_check CHECK (
    battery_health IS NULL OR (battery_health >= 0 AND battery_health <= 100)
);

-- An IMEI identifies exactly one physical handset, so a tenant can never hold
-- two units with the same one. Partial so the many non-phone units (laptops,
-- accessories) that have no IMEI don't collide on NULL.
CREATE UNIQUE INDEX item_serials_tenant_imei1_idx ON item_serials (tenant_id, imei1)
    WHERE imei1 IS NOT NULL;
CREATE UNIQUE INDEX item_serials_tenant_imei2_idx ON item_serials (tenant_id, imei2)
    WHERE imei2 IS NOT NULL;

-- IN_REPAIR: the unit is on a repair bench (ours or a customer's own device).
-- RMA: returned to the supplier and awaiting credit/replacement.
ALTER TABLE item_serials DROP CONSTRAINT item_serials_status_check;
ALTER TABLE item_serials ADD CONSTRAINT item_serials_status_check CHECK (
    status IN ('IN_STOCK', 'RESERVED', 'SOLD', 'RETURNED', 'DEFECTIVE',
               'WRITTEN_OFF', 'IN_REPAIR', 'RMA')
);

-- Powers the "scan an IMEI, show me this unit" counter lookup.
CREATE INDEX item_serials_tenant_condition_idx ON item_serials (tenant_id, unit_condition)
    WHERE status = 'IN_STOCK';

COMMENT ON COLUMN item_serials.battery_health IS
    'Percentage 0-100. Below 80 is the conventional trade-in markdown trigger.';
