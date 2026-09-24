-- ============================================================================
-- V22  Cashier shifts and the cash drawer.
--
-- Until now there was no answer to "is the money in the drawer the money the
-- system says should be there?". Cash came in against bills, repairs and
-- wholesale collections, and cash went out as refunds, but nothing tied a
-- till to a person and a time window, so a shortfall was invisible.
--
-- A shift owns an opening float and every non-sale cash movement (pay-in,
-- payout, safe drop). Expected cash is derived from the payments taken during
-- the shift rather than stored and maintained, so it cannot drift out of
-- agreement with the documents it came from. Closing records the counted
-- amount and freezes the shift - the Z-report.
-- ============================================================================

CREATE TABLE shifts (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id           uuid NOT NULL REFERENCES outlets (id) ON DELETE RESTRICT,
    shift_number        varchar(40) NOT NULL,
    status              varchar(12) NOT NULL DEFAULT 'OPEN',

    opened_by           uuid REFERENCES users (id) ON DELETE SET NULL,
    opened_at           timestamptz NOT NULL DEFAULT now(),
    opening_float       numeric(14, 2) NOT NULL DEFAULT 0,

    closed_by           uuid REFERENCES users (id) ON DELETE SET NULL,
    closed_at           timestamptz,
    -- Frozen at close: what the drawer should have held, what it actually held,
    -- and the difference. Kept rather than recomputed so a historic Z-report
    -- never changes after the fact.
    expected_cash       numeric(14, 2),
    counted_cash        numeric(14, 2),
    variance            numeric(14, 2),
    -- {"5000": 4, "1000": 12, ...} - the physical count, for recount disputes.
    denominations       jsonb,

    note                text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT shifts_tenant_number_key UNIQUE (tenant_id, shift_number),
    CONSTRAINT shifts_status_check CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT shifts_float_check CHECK (opening_float >= 0),
    -- A closed shift carries its whole reconciliation or none of it.
    CONSTRAINT shifts_closed_check CHECK (
        status <> 'CLOSED'
        OR (closed_at IS NOT NULL AND expected_cash IS NOT NULL
            AND counted_cash IS NOT NULL AND variance IS NOT NULL)
    )
);

-- One drawer per till at a time: opening a second shift on an outlet that
-- already has one open is what makes a count meaningless.
CREATE UNIQUE INDEX shifts_one_open_per_outlet_idx ON shifts (tenant_id, outlet_id)
    WHERE status = 'OPEN';
CREATE INDEX shifts_tenant_opened_idx ON shifts (tenant_id, opened_at DESC);


CREATE TABLE cash_movements (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    shift_id            uuid NOT NULL REFERENCES shifts (id) ON DELETE CASCADE,
    outlet_id           uuid REFERENCES outlets (id) ON DELETE SET NULL,
    movement_type       varchar(20) NOT NULL,
    amount              numeric(14, 2) NOT NULL,
    reason              varchar(240),
    reference           varchar(120),
    occurred_at         timestamptz NOT NULL DEFAULT now(),
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users (id) ON DELETE SET NULL,

    CONSTRAINT cash_movements_type_check CHECK (
        movement_type IN ('PAY_IN', 'PAYOUT', 'DROP')
    ),
    -- Direction is carried by movement_type, so the amount is always positive.
    CONSTRAINT cash_movements_amount_check CHECK (amount > 0)
);

CREATE INDEX cash_movements_shift_idx ON cash_movements (shift_id, occurred_at);


-- Payments are attributed to a shift by the till and the time window, so a
-- drawer count can be reconstructed without stamping every payment row.
CREATE INDEX payments_outlet_cash_idx ON payments (outlet_id, received_at)
    WHERE method = 'CASH' AND reversed_at IS NULL;


CREATE TRIGGER shifts_set_updated_at BEFORE UPDATE ON shifts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------------
-- Row-Level Security. V12's guard rail only runs once, so new tenant-owned
-- tables must bring their own policy or they would be readable across tenants.
-- ENABLE (not FORCE) matches the other tenant tables: the runtime role is a
-- non-owner, so the policy binds it, while migrations still run as owner.
-- ---------------------------------------------------------------------------
ALTER TABLE shifts ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON shifts
    FOR ALL
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE cash_movements ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON cash_movements
    FOR ALL
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

GRANT SELECT, INSERT, UPDATE, DELETE ON shifts, cash_movements TO "${app_db_user}";
