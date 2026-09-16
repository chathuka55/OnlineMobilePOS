-- ============================================================================
-- V5  Customers.
--
-- Consolidates the desktop app's two parallel models (`Customers` plus the
-- orphaned `WholesaleCustomers` table) into one entity with a customer_type
-- discriminator, so wholesale credit and retail history live side by side.
-- ============================================================================

CREATE TABLE customers (
    id                      uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id               uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    code                    varchar(30),
    display_name            varchar(160) NOT NULL,
    customer_type           varchar(20) NOT NULL DEFAULT 'RETAIL',
    phone_primary           varchar(32),
    phone_secondary         varchar(32),
    email                   citext,
    address_line1           varchar(180),
    address_line2           varchar(180),
    city                    varchar(90),
    tax_identifier          varchar(60),
    -- Denormalised running totals, maintained inside the same transaction as the
    -- documents that move them. Reconcilable from bills/payments at any time.
    credit_limit            numeric(14, 2) NOT NULL DEFAULT 0,
    outstanding_amount      numeric(14, 2) NOT NULL DEFAULT 0,
    lifetime_sales          numeric(14, 2) NOT NULL DEFAULT 0,
    loyalty_points          integer NOT NULL DEFAULT 0,
    default_tax_rate_id     uuid REFERENCES tax_rates (id) ON DELETE SET NULL,
    notes                   text,
    is_active               boolean NOT NULL DEFAULT true,
    deleted_at              timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid REFERENCES users (id) ON DELETE SET NULL,
    updated_by              uuid REFERENCES users (id) ON DELETE SET NULL,
    version                 bigint NOT NULL DEFAULT 0,

    CONSTRAINT customers_type_check CHECK (
        customer_type IN ('RETAIL', 'WHOLESALE', 'BOTH')
    ),
    CONSTRAINT customers_credit_limit_check CHECK (credit_limit >= 0),
    CONSTRAINT customers_loyalty_check CHECK (loyalty_points >= 0)
);

CREATE UNIQUE INDEX customers_tenant_code_idx ON customers (tenant_id, code)
    WHERE code IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX customers_tenant_active_idx ON customers (tenant_id, display_name)
    WHERE deleted_at IS NULL;
CREATE INDEX customers_tenant_phone_idx ON customers (tenant_id, phone_primary)
    WHERE phone_primary IS NOT NULL AND deleted_at IS NULL;
-- Trigram index powers the POS "type any part of a name" customer lookup.
CREATE INDEX customers_name_trgm_idx ON customers USING gin (display_name gin_trgm_ops);
CREATE INDEX customers_tenant_outstanding_idx ON customers (tenant_id, outstanding_amount DESC)
    WHERE outstanding_amount > 0;

COMMENT ON COLUMN customers.credit_limit IS
    'Zero means no credit allowed. Checked at wholesale checkout before a bill is accepted.';


-- Free-form timeline entries against a customer (calls, complaints, promises to pay).
CREATE TABLE customer_notes (
    id              uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id       uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    customer_id     uuid NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    body            text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX customer_notes_customer_idx ON customer_notes (customer_id, created_at DESC);


CREATE TRIGGER customers_set_updated_at BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
