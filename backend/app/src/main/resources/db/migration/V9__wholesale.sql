-- ============================================================================
-- V9  Wholesale / B2B invoicing on credit.
--
-- Replaces CHECK_BILLS, CHECK_BILL_ITEMS and CHECK_PAYMENTS. Two behavioural
-- fixes carried over from WholesalePanel:
--   * Stock is only consumed when the invoice is POSTED, not when a line is
--     typed into the grid.
--   * The credit limit is verified against live outstanding at post time.
-- Cheque tracking now lives in the shared `payments` table.
-- ============================================================================

CREATE TABLE wholesale_invoices (
    id                      uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id               uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id               uuid NOT NULL REFERENCES outlets (id) ON DELETE RESTRICT,
    invoice_number          varchar(40) NOT NULL,
    status                  varchar(20) NOT NULL DEFAULT 'DRAFT',

    customer_id             uuid NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    customer_name           varchar(160) NOT NULL,
    customer_phone          varchar(32),

    currency                char(3) NOT NULL DEFAULT 'LKR',
    subtotal                numeric(14, 2) NOT NULL DEFAULT 0,
    line_discount_total     numeric(14, 2) NOT NULL DEFAULT 0,
    invoice_discount_type   varchar(12) NOT NULL DEFAULT 'NONE',
    invoice_discount_input  numeric(14, 2) NOT NULL DEFAULT 0,
    invoice_discount_amount numeric(14, 2) NOT NULL DEFAULT 0,
    tax_total               numeric(14, 2) NOT NULL DEFAULT 0,
    grand_total             numeric(14, 2) NOT NULL DEFAULT 0,
    amount_paid             numeric(14, 2) NOT NULL DEFAULT 0,
    outstanding_amount      numeric(14, 2) NOT NULL DEFAULT 0,
    cost_of_goods           numeric(14, 2) NOT NULL DEFAULT 0,

    -- Credit terms snapshot, so changing the customer's limit later does not
    -- retroactively alter what was agreed on this invoice.
    credit_limit_at_issue   numeric(14, 2),
    payment_terms_days      smallint NOT NULL DEFAULT 0,
    due_date                date,

    issued_at               timestamptz NOT NULL DEFAULT now(),
    posted_at               timestamptz,
    settled_at              timestamptz,
    voided_at               timestamptz,
    void_reason             varchar(240),
    note                    text,
    idempotency_key         varchar(80),

    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid REFERENCES users (id) ON DELETE SET NULL,
    updated_by              uuid REFERENCES users (id) ON DELETE SET NULL,
    version                 bigint NOT NULL DEFAULT 0,

    CONSTRAINT wholesale_invoices_tenant_number_key UNIQUE (tenant_id, invoice_number),
    CONSTRAINT wholesale_invoices_status_check CHECK (
        status IN ('DRAFT', 'POSTED', 'PARTIALLY_PAID', 'SETTLED', 'OVERDUE', 'VOIDED')
    ),
    CONSTRAINT wholesale_invoices_discount_type_check CHECK (
        invoice_discount_type IN ('NONE', 'PERCENTAGE', 'FIXED')
    ),
    CONSTRAINT wholesale_invoices_amounts_check CHECK (
        subtotal >= 0 AND grand_total >= 0 AND amount_paid >= 0
    )
);

CREATE UNIQUE INDEX wholesale_invoices_idempotency_idx
    ON wholesale_invoices (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX wholesale_invoices_tenant_date_idx ON wholesale_invoices (tenant_id, issued_at DESC);
CREATE INDEX wholesale_invoices_customer_idx ON wholesale_invoices (customer_id, issued_at DESC);
-- Aged-receivables report and the collections worklist.
CREATE INDEX wholesale_invoices_outstanding_idx
    ON wholesale_invoices (tenant_id, due_date)
    WHERE outstanding_amount > 0 AND voided_at IS NULL;
CREATE INDEX wholesale_invoices_number_trgm_idx
    ON wholesale_invoices USING gin (invoice_number gin_trgm_ops);


CREATE TABLE wholesale_invoice_lines (
    id                      uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id               uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    wholesale_invoice_id    uuid NOT NULL REFERENCES wholesale_invoices (id) ON DELETE CASCADE,
    item_id                 uuid NOT NULL REFERENCES items (id) ON DELETE RESTRICT,
    line_number             smallint NOT NULL,

    item_sku                varchar(60) NOT NULL,
    item_name               varchar(200) NOT NULL,
    unit_cost               numeric(14, 2) NOT NULL DEFAULT 0,
    price_mode              varchar(20) NOT NULL DEFAULT 'WHOLESALE',

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
    line_total              numeric(14, 2) NOT NULL,
    warranty_label          varchar(60),
    quantity_returned       numeric(14, 3) NOT NULL DEFAULT 0,

    CONSTRAINT wholesale_invoice_lines_line_key UNIQUE (wholesale_invoice_id, line_number),
    CONSTRAINT wholesale_invoice_lines_quantity_check CHECK (quantity > 0),
    CONSTRAINT wholesale_invoice_lines_price_mode_check CHECK (
        price_mode IN ('RETAIL', 'WHOLESALE', 'CUSTOM')
    ),
    CONSTRAINT wholesale_invoice_lines_discount_type_check CHECK (
        discount_type IN ('NONE', 'PERCENTAGE', 'FIXED')
    ),
    CONSTRAINT wholesale_invoice_lines_returned_check CHECK (
        quantity_returned >= 0 AND quantity_returned <= quantity
    )
);

CREATE INDEX wholesale_invoice_lines_invoice_idx
    ON wholesale_invoice_lines (wholesale_invoice_id);
CREATE INDEX wholesale_invoice_lines_item_idx ON wholesale_invoice_lines (item_id);


CREATE TABLE wholesale_invoice_line_serials (
    id                          uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id                   uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    wholesale_invoice_line_id    uuid NOT NULL
        REFERENCES wholesale_invoice_lines (id) ON DELETE CASCADE,
    item_serial_id              uuid NOT NULL REFERENCES item_serials (id) ON DELETE RESTRICT,
    serial_number               varchar(120) NOT NULL,

    CONSTRAINT wholesale_line_serials_unique_key
        UNIQUE (wholesale_invoice_line_id, item_serial_id)
);


-- ---------------------------------------------------------------------------
-- customer_credit_ledger: append-only movement of a wholesale customer's
-- balance. customers.outstanding_amount is the cache; this is the proof, and it
-- is what lets support answer "why does this customer owe 42,000?".
-- ---------------------------------------------------------------------------
CREATE TABLE customer_credit_ledger (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    customer_id         uuid NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    entry_type          varchar(30) NOT NULL,
    amount_delta        numeric(14, 2) NOT NULL,
    balance_after       numeric(14, 2) NOT NULL,
    document_type       varchar(30),
    document_id         uuid,
    document_number     varchar(40),
    note                varchar(240),
    occurred_at         timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users (id) ON DELETE SET NULL,

    CONSTRAINT customer_credit_ledger_type_check CHECK (
        entry_type IN (
            'INVOICE', 'PAYMENT', 'CREDIT_NOTE', 'REFUND', 'WRITE_OFF',
            'CHEQUE_BOUNCE', 'OPENING_BALANCE', 'ADJUSTMENT'
        )
    ),
    CONSTRAINT customer_credit_ledger_delta_check CHECK (amount_delta <> 0)
);

CREATE INDEX customer_credit_ledger_customer_idx
    ON customer_credit_ledger (customer_id, occurred_at DESC);
CREATE INDEX customer_credit_ledger_document_idx
    ON customer_credit_ledger (document_type, document_id) WHERE document_id IS NOT NULL;

CREATE TRIGGER customer_credit_ledger_append_only
    BEFORE UPDATE OR DELETE ON customer_credit_ledger
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();


CREATE TRIGGER wholesale_invoices_set_updated_at BEFORE UPDATE ON wholesale_invoices
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
