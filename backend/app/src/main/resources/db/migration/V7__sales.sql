-- ============================================================================
-- V7  Retail sales: persisted carts, bills, payments, credit notes and refunds.
--
-- Notable departures from the desktop app:
--   * Held bills are rows, not a HashMap in BillingPanel, so they survive a
--     restart and are visible from any terminal.
--   * Bill lines reference item_id. The old schema stored only ItemName and
--     joined on UPPER(name), which broke silently on rename or duplicate names.
--   * bills.idempotency_key makes checkout safe to retry over a flaky link.
--   * Credit notes are first-class instead of being smuggled through
--     Refunds.RepairCode.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- carts / cart_lines: the in-progress sale on a terminal.
-- ---------------------------------------------------------------------------
CREATE TABLE carts (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id           uuid NOT NULL REFERENCES outlets (id) ON DELETE CASCADE,
    status              varchar(20) NOT NULL DEFAULT 'DRAFT',
    label               varchar(120),
    customer_id         uuid REFERENCES customers (id) ON DELETE SET NULL,
    customer_name       varchar(160),
    price_mode          varchar(20) NOT NULL DEFAULT 'RETAIL',
    note               text,
    held_at             timestamptz,
    converted_bill_id   uuid,
    expires_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users (id) ON DELETE SET NULL,
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT carts_status_check CHECK (
        status IN ('DRAFT', 'HELD', 'CONVERTED', 'ABANDONED')
    ),
    CONSTRAINT carts_price_mode_check CHECK (price_mode IN ('RETAIL', 'WHOLESALE')),
    CONSTRAINT carts_converted_has_bill_check CHECK (
        status <> 'CONVERTED' OR converted_bill_id IS NOT NULL
    )
);

CREATE INDEX carts_outlet_status_idx ON carts (outlet_id, status)
    WHERE status IN ('DRAFT', 'HELD');
CREATE INDEX carts_tenant_held_idx ON carts (tenant_id, held_at DESC) WHERE status = 'HELD';
CREATE INDEX carts_expiry_idx ON carts (expires_at) WHERE status = 'DRAFT';


CREATE TABLE cart_lines (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    cart_id             uuid NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    item_id             uuid NOT NULL REFERENCES items (id) ON DELETE RESTRICT,
    line_number         smallint NOT NULL,
    description         varchar(200),
    quantity            numeric(14, 3) NOT NULL DEFAULT 1,
    unit_price          numeric(14, 2) NOT NULL DEFAULT 0,
    discount_type       varchar(12) NOT NULL DEFAULT 'NONE',
    discount_input      numeric(14, 2) NOT NULL DEFAULT 0,
    tax_rate_id         uuid REFERENCES tax_rates (id) ON DELETE SET NULL,
    warranty_label      varchar(60),
    -- Serial ids chosen for this line before the sale is committed.
    serial_ids          uuid[] NOT NULL DEFAULT '{}',
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT cart_lines_cart_line_key UNIQUE (cart_id, line_number),
    CONSTRAINT cart_lines_quantity_check CHECK (quantity > 0),
    CONSTRAINT cart_lines_discount_type_check CHECK (
        discount_type IN ('NONE', 'PERCENTAGE', 'FIXED')
    ),
    CONSTRAINT cart_lines_discount_input_check CHECK (discount_input >= 0)
);

CREATE INDEX cart_lines_cart_idx ON cart_lines (cart_id);


-- ---------------------------------------------------------------------------
-- bills: a committed retail sale.
-- ---------------------------------------------------------------------------
CREATE TABLE bills (
    id                      uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id               uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id               uuid NOT NULL REFERENCES outlets (id) ON DELETE RESTRICT,
    bill_number             varchar(40) NOT NULL,
    status                  varchar(20) NOT NULL DEFAULT 'COMPLETED',
    channel                 varchar(20) NOT NULL DEFAULT 'RETAIL',
    price_mode              varchar(20) NOT NULL DEFAULT 'RETAIL',

    customer_id             uuid REFERENCES customers (id) ON DELETE SET NULL,
    -- Snapshot: a walk-in has no customer row, and a later rename must not
    -- rewrite a printed invoice.
    customer_name           varchar(160) NOT NULL DEFAULT 'Walk-in Customer',
    customer_phone          varchar(32),

    currency                char(3) NOT NULL DEFAULT 'LKR',
    subtotal                numeric(14, 2) NOT NULL DEFAULT 0,
    line_discount_total     numeric(14, 2) NOT NULL DEFAULT 0,
    bill_discount_type      varchar(12) NOT NULL DEFAULT 'NONE',
    bill_discount_input     numeric(14, 2) NOT NULL DEFAULT 0,
    bill_discount_amount    numeric(14, 2) NOT NULL DEFAULT 0,
    tax_total               numeric(14, 2) NOT NULL DEFAULT 0,
    rounding_adjustment     numeric(14, 2) NOT NULL DEFAULT 0,
    grand_total             numeric(14, 2) NOT NULL DEFAULT 0,
    credit_applied          numeric(14, 2) NOT NULL DEFAULT 0,
    amount_paid             numeric(14, 2) NOT NULL DEFAULT 0,
    -- Positive means the customer still owes; negative means change is due.
    -- The desktop app used the opposite sign, which confused every report.
    balance_due             numeric(14, 2) NOT NULL DEFAULT 0,
    cost_of_goods           numeric(14, 2) NOT NULL DEFAULT 0,

    note                    text,
    billed_at               timestamptz NOT NULL DEFAULT now(),
    due_date                date,
    voided_at               timestamptz,
    void_reason             varchar(240),
    source_cart_id          uuid REFERENCES carts (id) ON DELETE SET NULL,
    -- Client-generated. Lets a terminal safely resend a checkout it never got a
    -- response for.
    idempotency_key         varchar(80),
    device_id               varchar(120),

    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid REFERENCES users (id) ON DELETE SET NULL,
    updated_by              uuid REFERENCES users (id) ON DELETE SET NULL,
    version                 bigint NOT NULL DEFAULT 0,

    CONSTRAINT bills_tenant_number_key UNIQUE (tenant_id, bill_number),
    CONSTRAINT bills_status_check CHECK (
        status IN ('COMPLETED', 'PARTIALLY_PAID', 'UNPAID', 'VOIDED', 'REFUNDED', 'PARTIALLY_REFUNDED')
    ),
    CONSTRAINT bills_channel_check CHECK (channel IN ('RETAIL', 'WHOLESALE', 'REPAIR', 'ONLINE')),
    CONSTRAINT bills_price_mode_check CHECK (price_mode IN ('RETAIL', 'WHOLESALE')),
    CONSTRAINT bills_discount_type_check CHECK (
        bill_discount_type IN ('NONE', 'PERCENTAGE', 'FIXED')
    ),
    CONSTRAINT bills_amounts_check CHECK (
        subtotal >= 0 AND tax_total >= 0 AND grand_total >= 0
        AND credit_applied >= 0 AND amount_paid >= 0
    )
);

CREATE UNIQUE INDEX bills_idempotency_idx ON bills (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX bills_tenant_date_idx ON bills (tenant_id, billed_at DESC);
CREATE INDEX bills_outlet_date_idx ON bills (outlet_id, billed_at DESC);
CREATE INDEX bills_customer_idx ON bills (customer_id, billed_at DESC)
    WHERE customer_id IS NOT NULL;
CREATE INDEX bills_receivables_idx ON bills (tenant_id, balance_due)
    WHERE balance_due > 0 AND voided_at IS NULL;
CREATE INDEX bills_number_trgm_idx ON bills USING gin (bill_number gin_trgm_ops);
-- A plain (billed_at::date) index expression won't work: casting timestamptz
-- to date depends on the session's TimeZone setting, so Postgres classifies
-- it STABLE rather than IMMUTABLE and refuses it in an index. The app always
-- writes and reads timestamps in UTC (see hibernate.jdbc.time_zone above the
-- Flyway config), so fixing the zone here is safe to mark IMMUTABLE.
CREATE OR REPLACE FUNCTION billed_date_utc(ts timestamptz)
RETURNS date
LANGUAGE sql
IMMUTABLE
AS $$ SELECT (ts AT TIME ZONE 'UTC')::date $$;

-- Supports "today's sales" and daily Z-reports on a date basis.
CREATE INDEX bills_tenant_billed_date_idx ON bills (tenant_id, billed_date_utc(billed_at));

COMMENT ON COLUMN bills.cost_of_goods IS
    'Sum of line cost snapshots, so gross profit needs no join back to items.';


CREATE TABLE bill_lines (
    id                      uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id               uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    bill_id                 uuid NOT NULL REFERENCES bills (id) ON DELETE CASCADE,
    item_id                 uuid NOT NULL REFERENCES items (id) ON DELETE RESTRICT,
    line_number             smallint NOT NULL,

    -- Immutable snapshots taken at the moment of sale.
    item_sku                varchar(60) NOT NULL,
    item_name               varchar(200) NOT NULL,
    unit_cost               numeric(14, 2) NOT NULL DEFAULT 0,

    quantity                numeric(14, 3) NOT NULL,
    unit_price              numeric(14, 2) NOT NULL,
    gross_amount            numeric(14, 2) NOT NULL,
    discount_type           varchar(12) NOT NULL DEFAULT 'NONE',
    discount_input          numeric(14, 2) NOT NULL DEFAULT 0,
    discount_amount         numeric(14, 2) NOT NULL DEFAULT 0,
    net_amount              numeric(14, 2) NOT NULL,
    tax_rate_id             uuid REFERENCES tax_rates (id) ON DELETE SET NULL,
    tax_rate_percent        numeric(9, 4) NOT NULL DEFAULT 0,
    tax_amount              numeric(14, 2) NOT NULL DEFAULT 0,
    tax_inclusive           boolean NOT NULL DEFAULT false,
    line_total              numeric(14, 2) NOT NULL,

    warranty_label          varchar(60),
    warranty_months         smallint NOT NULL DEFAULT 0,
    warranty_ends_on        date,
    quantity_returned       numeric(14, 3) NOT NULL DEFAULT 0,

    CONSTRAINT bill_lines_bill_line_key UNIQUE (bill_id, line_number),
    CONSTRAINT bill_lines_quantity_check CHECK (quantity > 0),
    CONSTRAINT bill_lines_discount_type_check CHECK (
        discount_type IN ('NONE', 'PERCENTAGE', 'FIXED')
    ),
    -- A discount can never exceed the line's gross value.
    CONSTRAINT bill_lines_discount_bounds_check CHECK (
        discount_amount >= 0 AND discount_amount <= gross_amount
    ),
    CONSTRAINT bill_lines_returned_bounds_check CHECK (
        quantity_returned >= 0 AND quantity_returned <= quantity
    )
);

CREATE INDEX bill_lines_bill_idx ON bill_lines (bill_id);
CREATE INDEX bill_lines_item_idx ON bill_lines (item_id);
CREATE INDEX bill_lines_tenant_item_idx ON bill_lines (tenant_id, item_id);


CREATE TABLE bill_line_serials (
    id              uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id       uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    bill_line_id    uuid NOT NULL REFERENCES bill_lines (id) ON DELETE CASCADE,
    item_serial_id  uuid NOT NULL REFERENCES item_serials (id) ON DELETE RESTRICT,
    serial_number   varchar(120) NOT NULL,
    returned_at     timestamptz,

    CONSTRAINT bill_line_serials_unique_key UNIQUE (bill_line_id, item_serial_id)
);

CREATE INDEX bill_line_serials_serial_idx ON bill_line_serials (item_serial_id);


-- ---------------------------------------------------------------------------
-- payments: split tenders against any document. Polymorphic so retail bills,
-- repair orders and wholesale invoices share one receipts ledger, unlike the
-- desktop app which had Payments, CHECK_PAYMENTS and inline repair fields.
-- ---------------------------------------------------------------------------
CREATE TABLE payments (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id           uuid REFERENCES outlets (id) ON DELETE SET NULL,
    payment_number      varchar(40),
    document_type       varchar(30) NOT NULL,
    document_id         uuid NOT NULL,
    customer_id         uuid REFERENCES customers (id) ON DELETE SET NULL,
    method              varchar(30) NOT NULL,
    direction           varchar(10) NOT NULL DEFAULT 'IN',
    amount              numeric(14, 2) NOT NULL,
    currency            char(3) NOT NULL DEFAULT 'LKR',
    tendered_amount     numeric(14, 2),
    change_amount       numeric(14, 2) NOT NULL DEFAULT 0,

    -- Card / digital references
    reference           varchar(120),
    card_last4          char(4),

    -- Cheque details, previously spread across CHECK_BILLS columns
    bank_name           varchar(120),
    cheque_number       varchar(60),
    cheque_date         date,
    cheque_status       varchar(20),
    cleared_at          timestamptz,
    bounced_at          timestamptz,
    bounce_reason       varchar(240),

    received_at         timestamptz NOT NULL DEFAULT now(),
    reversed_at         timestamptz,
    reversal_reason     varchar(240),
    note                text,
    idempotency_key     varchar(80),
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users (id) ON DELETE SET NULL,
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT payments_document_type_check CHECK (
        document_type IN ('BILL', 'REPAIR_ORDER', 'WHOLESALE_INVOICE', 'SUBSCRIPTION_INVOICE')
    ),
    CONSTRAINT payments_method_check CHECK (
        method IN ('CASH', 'CARD', 'BANK_TRANSFER', 'CHEQUE', 'MOBILE_WALLET', 'CREDIT_NOTE', 'STORE_CREDIT', 'OTHER')
    ),
    CONSTRAINT payments_direction_check CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT payments_amount_check CHECK (amount > 0),
    CONSTRAINT payments_cheque_status_check CHECK (
        cheque_status IS NULL OR cheque_status IN ('PENDING', 'DEPOSITED', 'CLEARED', 'BOUNCED', 'CANCELLED')
    ),
    -- A cheque tender must identify the cheque.
    CONSTRAINT payments_cheque_details_check CHECK (
        method <> 'CHEQUE' OR (cheque_number IS NOT NULL AND cheque_date IS NOT NULL)
    )
);

CREATE UNIQUE INDEX payments_idempotency_idx ON payments (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX payments_document_idx ON payments (document_type, document_id);
CREATE INDEX payments_tenant_date_idx ON payments (tenant_id, received_at DESC);
CREATE INDEX payments_customer_idx ON payments (customer_id, received_at DESC)
    WHERE customer_id IS NOT NULL;
-- The cheque management screen lists everything not yet cleared.
CREATE INDEX payments_pending_cheques_idx ON payments (tenant_id, cheque_date)
    WHERE method = 'CHEQUE' AND cheque_status IN ('PENDING', 'DEPOSITED');


-- ---------------------------------------------------------------------------
-- credit_notes: redeemable store credit with a running balance.
-- ---------------------------------------------------------------------------
CREATE TABLE credit_notes (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id           uuid REFERENCES outlets (id) ON DELETE SET NULL,
    credit_note_number  varchar(40) NOT NULL,
    customer_id         uuid REFERENCES customers (id) ON DELETE SET NULL,
    customer_name       varchar(160),
    customer_phone      varchar(32),
    -- Explicit source instead of the desktop app's overloaded RepairCode column.
    source_type         varchar(30) NOT NULL,
    source_id           uuid,
    source_number       varchar(40),
    currency            char(3) NOT NULL DEFAULT 'LKR',
    issued_amount       numeric(14, 2) NOT NULL,
    balance_amount      numeric(14, 2) NOT NULL,
    status              varchar(20) NOT NULL DEFAULT 'ACTIVE',
    reason              varchar(240),
    expires_on          date,
    issued_at           timestamptz NOT NULL DEFAULT now(),
    voided_at           timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users (id) ON DELETE SET NULL,
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT credit_notes_tenant_number_key UNIQUE (tenant_id, credit_note_number),
    CONSTRAINT credit_notes_source_type_check CHECK (
        source_type IN ('BILL', 'REPAIR_ORDER', 'WHOLESALE_INVOICE', 'MANUAL')
    ),
    CONSTRAINT credit_notes_status_check CHECK (
        status IN ('ACTIVE', 'PARTIALLY_USED', 'FULLY_USED', 'EXPIRED', 'VOIDED')
    ),
    CONSTRAINT credit_notes_amount_check CHECK (issued_amount > 0),
    -- The balance can never drift outside [0, issued].
    CONSTRAINT credit_notes_balance_bounds_check CHECK (
        balance_amount >= 0 AND balance_amount <= issued_amount
    )
);

CREATE INDEX credit_notes_customer_idx ON credit_notes (customer_id)
    WHERE status IN ('ACTIVE', 'PARTIALLY_USED');
CREATE INDEX credit_notes_tenant_status_idx ON credit_notes (tenant_id, status);
CREATE INDEX credit_notes_source_idx ON credit_notes (source_type, source_id)
    WHERE source_id IS NOT NULL;
CREATE INDEX credit_notes_number_trgm_idx ON credit_notes USING gin (credit_note_number gin_trgm_ops);


CREATE TABLE credit_note_redemptions (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    credit_note_id      uuid NOT NULL REFERENCES credit_notes (id) ON DELETE CASCADE,
    document_type       varchar(30) NOT NULL,
    document_id         uuid NOT NULL,
    document_number     varchar(40),
    amount              numeric(14, 2) NOT NULL,
    redeemed_at         timestamptz NOT NULL DEFAULT now(),
    reversed_at         timestamptz,
    created_by          uuid REFERENCES users (id) ON DELETE SET NULL,

    CONSTRAINT credit_note_redemptions_amount_check CHECK (amount > 0),
    CONSTRAINT credit_note_redemptions_document_type_check CHECK (
        document_type IN ('BILL', 'REPAIR_ORDER', 'WHOLESALE_INVOICE')
    )
);

CREATE INDEX credit_note_redemptions_note_idx ON credit_note_redemptions (credit_note_id);
CREATE INDEX credit_note_redemptions_document_idx
    ON credit_note_redemptions (document_type, document_id);


-- ---------------------------------------------------------------------------
-- refunds: money or credit returned to a customer, with the lines that came
-- back so stock restoration is explicit and partially refundable.
-- ---------------------------------------------------------------------------
CREATE TABLE refunds (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id           uuid REFERENCES outlets (id) ON DELETE SET NULL,
    refund_number       varchar(40) NOT NULL,
    source_type         varchar(30) NOT NULL,
    source_id           uuid NOT NULL,
    source_number       varchar(40),
    customer_id         uuid REFERENCES customers (id) ON DELETE SET NULL,
    customer_name       varchar(160),
    refund_scope        varchar(12) NOT NULL DEFAULT 'PARTIAL',
    settlement          varchar(20) NOT NULL DEFAULT 'CASH',
    currency            char(3) NOT NULL DEFAULT 'LKR',
    refund_amount       numeric(14, 2) NOT NULL,
    restock             boolean NOT NULL DEFAULT true,
    reason              varchar(240),
    note                text,
    credit_note_id      uuid REFERENCES credit_notes (id) ON DELETE SET NULL,
    refunded_at         timestamptz NOT NULL DEFAULT now(),
    idempotency_key     varchar(80),
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users (id) ON DELETE SET NULL,
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT refunds_tenant_number_key UNIQUE (tenant_id, refund_number),
    CONSTRAINT refunds_source_type_check CHECK (
        source_type IN ('BILL', 'REPAIR_ORDER', 'WHOLESALE_INVOICE')
    ),
    CONSTRAINT refunds_scope_check CHECK (refund_scope IN ('FULL', 'PARTIAL')),
    CONSTRAINT refunds_settlement_check CHECK (
        settlement IN ('CASH', 'CARD_REVERSAL', 'BANK_TRANSFER', 'CREDIT_NOTE', 'CHEQUE')
    ),
    CONSTRAINT refunds_amount_check CHECK (refund_amount > 0),
    CONSTRAINT refunds_credit_note_link_check CHECK (
        settlement <> 'CREDIT_NOTE' OR credit_note_id IS NOT NULL
    )
);

CREATE UNIQUE INDEX refunds_idempotency_idx ON refunds (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX refunds_source_idx ON refunds (source_type, source_id);
CREATE INDEX refunds_tenant_date_idx ON refunds (tenant_id, refunded_at DESC);


CREATE TABLE refund_lines (
    id              uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id       uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    refund_id       uuid NOT NULL REFERENCES refunds (id) ON DELETE CASCADE,
    bill_line_id    uuid REFERENCES bill_lines (id) ON DELETE SET NULL,
    item_id         uuid REFERENCES items (id) ON DELETE SET NULL,
    item_name       varchar(200) NOT NULL,
    quantity        numeric(14, 3) NOT NULL,
    unit_price      numeric(14, 2) NOT NULL DEFAULT 0,
    line_total      numeric(14, 2) NOT NULL DEFAULT 0,
    restocked       boolean NOT NULL DEFAULT true,
    condition_note  varchar(240),

    CONSTRAINT refund_lines_quantity_check CHECK (quantity > 0)
);

CREATE INDEX refund_lines_refund_idx ON refund_lines (refund_id);
CREATE INDEX refund_lines_bill_line_idx ON refund_lines (bill_line_id);


-- carts.converted_bill_id is declared before bills exists, so the FK is added here.
ALTER TABLE carts
    ADD CONSTRAINT carts_converted_bill_fk
    FOREIGN KEY (converted_bill_id) REFERENCES bills (id) ON DELETE SET NULL;


CREATE TRIGGER carts_set_updated_at BEFORE UPDATE ON carts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER cart_lines_set_updated_at BEFORE UPDATE ON cart_lines
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER bills_set_updated_at BEFORE UPDATE ON bills
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER credit_notes_set_updated_at BEFORE UPDATE ON credit_notes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
