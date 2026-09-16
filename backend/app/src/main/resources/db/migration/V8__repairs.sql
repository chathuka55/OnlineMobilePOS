-- ============================================================================
-- V8  Repair / service orders.
--
-- Status is a constrained enum. In the desktop app the combo box wrote
-- 'InProgress' while ReportsPanel compared against 'In Progress', so in-progress
-- jobs silently vanished from the daily summary.
-- ============================================================================

CREATE TABLE repair_orders (
    id                      uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id               uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id               uuid NOT NULL REFERENCES outlets (id) ON DELETE RESTRICT,
    repair_number           varchar(40) NOT NULL,
    status                  varchar(24) NOT NULL DEFAULT 'RECEIVED',

    customer_id             uuid REFERENCES customers (id) ON DELETE SET NULL,
    customer_name           varchar(160) NOT NULL,
    customer_phone          varchar(32),

    -- The device being serviced
    device_type             varchar(80),
    device_brand            varchar(80),
    device_model            varchar(120),
    device_serial           varchar(120),
    repair_type             varchar(160),
    reported_fault          text,
    diagnosis               text,
    -- Multi-select checklists sourced from repair_conditions.txt / borrowed_items.txt
    device_conditions       text[] NOT NULL DEFAULT '{}',
    borrowed_items          text[] NOT NULL DEFAULT '{}',
    accessories_note        text,
    device_passcode         varchar(120),

    currency                char(3) NOT NULL DEFAULT 'LKR',
    service_charge          numeric(14, 2) NOT NULL DEFAULT 0,
    parts_subtotal          numeric(14, 2) NOT NULL DEFAULT 0,
    line_discount_total     numeric(14, 2) NOT NULL DEFAULT 0,
    discount_amount         numeric(14, 2) NOT NULL DEFAULT 0,
    tax_total               numeric(14, 2) NOT NULL DEFAULT 0,
    grand_total             numeric(14, 2) NOT NULL DEFAULT 0,
    -- Deposit taken at intake. Revenue is only recognised once the job leaves
    -- RECEIVED/DIAGNOSING, which matches RepairsDAO.calculateRepairSales.
    advance_paid            numeric(14, 2) NOT NULL DEFAULT 0,
    amount_paid             numeric(14, 2) NOT NULL DEFAULT 0,
    balance_due             numeric(14, 2) NOT NULL DEFAULT 0,
    parts_cost              numeric(14, 2) NOT NULL DEFAULT 0,

    estimated_cost          numeric(14, 2),
    warranty_days           smallint NOT NULL DEFAULT 0,
    warranty_ends_on        date,

    received_at             timestamptz NOT NULL DEFAULT now(),
    promised_at             timestamptz,
    completed_at            timestamptz,
    delivered_at            timestamptz,
    cancelled_at            timestamptz,
    cancel_reason           varchar(240),

    technician_id           uuid REFERENCES users (id) ON DELETE SET NULL,
    note                    text,
    idempotency_key         varchar(80),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid REFERENCES users (id) ON DELETE SET NULL,
    updated_by              uuid REFERENCES users (id) ON DELETE SET NULL,
    version                 bigint NOT NULL DEFAULT 0,

    CONSTRAINT repair_orders_tenant_number_key UNIQUE (tenant_id, repair_number),
    CONSTRAINT repair_orders_status_check CHECK (
        status IN (
            'RECEIVED', 'DIAGNOSING', 'AWAITING_APPROVAL', 'AWAITING_PARTS',
            'IN_PROGRESS', 'COMPLETED', 'DELIVERED', 'CANCELLED', 'IRREPARABLE'
        )
    ),
    CONSTRAINT repair_orders_amounts_check CHECK (
        service_charge >= 0 AND parts_subtotal >= 0 AND grand_total >= 0 AND amount_paid >= 0
    )
);

CREATE UNIQUE INDEX repair_orders_idempotency_idx ON repair_orders (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX repair_orders_tenant_status_idx ON repair_orders (tenant_id, status);
-- The technician work queue: everything not yet finished, oldest first.
CREATE INDEX repair_orders_open_queue_idx ON repair_orders (tenant_id, received_at)
    WHERE status NOT IN ('DELIVERED', 'CANCELLED');
CREATE INDEX repair_orders_customer_idx ON repair_orders (customer_id, received_at DESC)
    WHERE customer_id IS NOT NULL;
CREATE INDEX repair_orders_technician_idx ON repair_orders (technician_id, status)
    WHERE technician_id IS NOT NULL;
CREATE INDEX repair_orders_receivables_idx ON repair_orders (tenant_id, balance_due)
    WHERE balance_due > 0;
CREATE INDEX repair_orders_number_trgm_idx ON repair_orders USING gin (repair_number gin_trgm_ops);
CREATE INDEX repair_orders_device_serial_idx ON repair_orders (tenant_id, device_serial)
    WHERE device_serial IS NOT NULL;


-- Parts and labour charged to the job.
CREATE TABLE repair_order_lines (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    repair_order_id     uuid NOT NULL REFERENCES repair_orders (id) ON DELETE CASCADE,
    line_number         smallint NOT NULL,
    line_type           varchar(12) NOT NULL DEFAULT 'PART',
    item_id             uuid REFERENCES items (id) ON DELETE SET NULL,
    item_sku            varchar(60),
    description         varchar(200) NOT NULL,
    unit_cost           numeric(14, 2) NOT NULL DEFAULT 0,
    quantity            numeric(14, 3) NOT NULL DEFAULT 1,
    unit_price          numeric(14, 2) NOT NULL DEFAULT 0,
    gross_amount        numeric(14, 2) NOT NULL DEFAULT 0,
    discount_type       varchar(12) NOT NULL DEFAULT 'NONE',
    discount_input      numeric(14, 2) NOT NULL DEFAULT 0,
    discount_amount     numeric(14, 2) NOT NULL DEFAULT 0,
    net_amount          numeric(14, 2) NOT NULL DEFAULT 0,
    tax_rate_id         uuid REFERENCES tax_rates (id) ON DELETE SET NULL,
    tax_rate_percent    numeric(9, 4) NOT NULL DEFAULT 0,
    tax_amount          numeric(14, 2) NOT NULL DEFAULT 0,
    line_total          numeric(14, 2) NOT NULL DEFAULT 0,
    warranty_label      varchar(60),

    CONSTRAINT repair_order_lines_line_key UNIQUE (repair_order_id, line_number),
    CONSTRAINT repair_order_lines_type_check CHECK (line_type IN ('PART', 'LABOUR', 'SERVICE')),
    CONSTRAINT repair_order_lines_quantity_check CHECK (quantity > 0),
    CONSTRAINT repair_order_lines_discount_type_check CHECK (
        discount_type IN ('NONE', 'PERCENTAGE', 'FIXED')
    ),
    -- Only a stock part consumes inventory, so only it needs an item link.
    CONSTRAINT repair_order_lines_part_needs_item_check CHECK (
        line_type <> 'PART' OR item_id IS NOT NULL
    )
);

CREATE INDEX repair_order_lines_order_idx ON repair_order_lines (repair_order_id);
CREATE INDEX repair_order_lines_item_idx ON repair_order_lines (item_id)
    WHERE item_id IS NOT NULL;


CREATE TABLE repair_order_line_serials (
    id                      uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id               uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    repair_order_line_id    uuid NOT NULL REFERENCES repair_order_lines (id) ON DELETE CASCADE,
    item_serial_id          uuid NOT NULL REFERENCES item_serials (id) ON DELETE RESTRICT,
    serial_number           varchar(120) NOT NULL,

    CONSTRAINT repair_order_line_serials_unique_key UNIQUE (repair_order_line_id, item_serial_id)
);


-- Every status transition, for SLA reporting and customer-facing history.
CREATE TABLE repair_status_history (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    repair_order_id     uuid NOT NULL REFERENCES repair_orders (id) ON DELETE CASCADE,
    from_status         varchar(24),
    to_status           varchar(24) NOT NULL,
    note                text,
    changed_at          timestamptz NOT NULL DEFAULT now(),
    changed_by          uuid REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX repair_status_history_order_idx
    ON repair_status_history (repair_order_id, changed_at DESC);


CREATE TRIGGER repair_orders_set_updated_at BEFORE UPDATE ON repair_orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
