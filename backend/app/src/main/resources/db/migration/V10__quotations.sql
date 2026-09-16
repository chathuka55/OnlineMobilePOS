-- ============================================================================
-- V10  Quotations / estimates.
--
-- Adds the convert-to-bill link the desktop app lacked; QuotationsPanel forced
-- staff to retype every line into the billing screen. No stock is reserved.
-- ============================================================================

CREATE TABLE quotations (
    id                      uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id               uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id               uuid NOT NULL REFERENCES outlets (id) ON DELETE RESTRICT,
    quotation_number        varchar(40) NOT NULL,
    status                  varchar(20) NOT NULL DEFAULT 'DRAFT',

    customer_id             uuid REFERENCES customers (id) ON DELETE SET NULL,
    customer_name           varchar(160) NOT NULL,
    customer_phone          varchar(32),
    customer_email          citext,

    currency                char(3) NOT NULL DEFAULT 'LKR',
    price_mode              varchar(20) NOT NULL DEFAULT 'RETAIL',
    subtotal                numeric(14, 2) NOT NULL DEFAULT 0,
    line_discount_total     numeric(14, 2) NOT NULL DEFAULT 0,
    quote_discount_type     varchar(12) NOT NULL DEFAULT 'NONE',
    quote_discount_input    numeric(14, 2) NOT NULL DEFAULT 0,
    quote_discount_amount   numeric(14, 2) NOT NULL DEFAULT 0,
    tax_total               numeric(14, 2) NOT NULL DEFAULT 0,
    grand_total             numeric(14, 2) NOT NULL DEFAULT 0,

    quoted_at               timestamptz NOT NULL DEFAULT now(),
    valid_until             date,
    sent_at                 timestamptz,
    accepted_at             timestamptz,
    rejected_at             timestamptz,
    converted_at            timestamptz,
    converted_bill_id       uuid REFERENCES bills (id) ON DELETE SET NULL,
    terms                   text,
    note                    text,

    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid REFERENCES users (id) ON DELETE SET NULL,
    updated_by              uuid REFERENCES users (id) ON DELETE SET NULL,
    version                 bigint NOT NULL DEFAULT 0,

    CONSTRAINT quotations_tenant_number_key UNIQUE (tenant_id, quotation_number),
    CONSTRAINT quotations_status_check CHECK (
        status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CONVERTED', 'CANCELLED')
    ),
    CONSTRAINT quotations_price_mode_check CHECK (price_mode IN ('RETAIL', 'WHOLESALE')),
    CONSTRAINT quotations_discount_type_check CHECK (
        quote_discount_type IN ('NONE', 'PERCENTAGE', 'FIXED')
    ),
    -- A converted quote must record which bill it became.
    CONSTRAINT quotations_converted_link_check CHECK (
        status <> 'CONVERTED' OR converted_bill_id IS NOT NULL
    )
);

CREATE INDEX quotations_tenant_date_idx ON quotations (tenant_id, quoted_at DESC);
CREATE INDEX quotations_tenant_status_idx ON quotations (tenant_id, status);
CREATE INDEX quotations_customer_idx ON quotations (customer_id, quoted_at DESC)
    WHERE customer_id IS NOT NULL;
-- Drives the nightly job that expires stale quotes.
CREATE INDEX quotations_expiry_idx ON quotations (valid_until)
    WHERE status IN ('DRAFT', 'SENT');
CREATE INDEX quotations_number_trgm_idx ON quotations USING gin (quotation_number gin_trgm_ops);


CREATE TABLE quotation_lines (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    quotation_id        uuid NOT NULL REFERENCES quotations (id) ON DELETE CASCADE,
    item_id             uuid REFERENCES items (id) ON DELETE SET NULL,
    line_number         smallint NOT NULL,

    item_sku            varchar(60),
    item_name           varchar(200) NOT NULL,
    description         text,

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

    CONSTRAINT quotation_lines_line_key UNIQUE (quotation_id, line_number),
    CONSTRAINT quotation_lines_quantity_check CHECK (quantity > 0),
    CONSTRAINT quotation_lines_discount_type_check CHECK (
        discount_type IN ('NONE', 'PERCENTAGE', 'FIXED')
    )
);

CREATE INDEX quotation_lines_quotation_idx ON quotation_lines (quotation_id);


CREATE TRIGGER quotations_set_updated_at BEFORE UPDATE ON quotations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
