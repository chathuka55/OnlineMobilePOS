-- ============================================================================
-- V2  Tenants, outlets, per-tenant settings, document numbering, tax rates,
--     and configurable lookup lists.
--
-- Replaces the legacy single-shop `ShopDetails` table plus the loose files the
-- desktop app read at runtime: config.properties, invoice_terms.txt,
-- repair_types.txt, repair_conditions.txt, borrowed_items.txt.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- tenants: one subscribing business. Deliberately NOT under RLS - it is the
-- root of the isolation tree and is only reachable by the platform admin API
-- and by the auth flow resolving a login to its tenant.
-- ---------------------------------------------------------------------------
CREATE TABLE tenants (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    slug                citext NOT NULL,
    business_name       varchar(160) NOT NULL,
    legal_name          varchar(160),
    tax_identifier      varchar(60),
    status              varchar(20) NOT NULL DEFAULT 'TRIAL',
    default_currency    char(3) NOT NULL DEFAULT 'LKR',
    default_locale      varchar(16) NOT NULL DEFAULT 'en-LK',
    time_zone           varchar(64) NOT NULL DEFAULT 'Asia/Colombo',
    contact_email       citext NOT NULL,
    contact_phone       varchar(32),
    onboarded_at        timestamptz,
    suspended_at        timestamptz,
    suspension_reason   text,
    deleted_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT tenants_slug_key UNIQUE (slug),
    CONSTRAINT tenants_status_check CHECK (
        status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')
    ),
    CONSTRAINT tenants_slug_format_check CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$')
);

CREATE INDEX tenants_status_idx ON tenants (status) WHERE deleted_at IS NULL;

COMMENT ON COLUMN tenants.status IS
    'PAST_DUE and SUSPENDED downgrade the tenant to read-only; CANCELLED blocks login entirely.';


-- ---------------------------------------------------------------------------
-- outlets: physical shop / branch. v1 creates exactly one per tenant, but every
-- transactional table carries outlet_id so multi-branch needs no migration.
-- ---------------------------------------------------------------------------
CREATE TABLE outlets (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    code                varchar(24) NOT NULL,
    name                varchar(120) NOT NULL,
    is_default          boolean NOT NULL DEFAULT false,
    address_line1       varchar(180),
    address_line2       varchar(180),
    city                varchar(90),
    phone_primary       varchar(32),
    phone_secondary     varchar(32),
    email               citext,
    website             varchar(180),
    logo_object_key     varchar(320),
    receipt_footer      text,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT outlets_tenant_code_key UNIQUE (tenant_id, code)
);

CREATE INDEX outlets_tenant_idx ON outlets (tenant_id);
-- At most one default outlet per tenant.
CREATE UNIQUE INDEX outlets_one_default_per_tenant_idx
    ON outlets (tenant_id) WHERE is_default;


-- ---------------------------------------------------------------------------
-- tenant_settings: typed key/value store for everything the desktop app kept in
-- .properties and .txt files (printer name, invoice terms, lock timeout, ...).
-- ---------------------------------------------------------------------------
CREATE TABLE tenant_settings (
    id              uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id       uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id       uuid REFERENCES outlets (id) ON DELETE CASCADE,
    setting_key     varchar(120) NOT NULL,
    setting_value   text,
    value_type      varchar(16) NOT NULL DEFAULT 'STRING',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         bigint NOT NULL DEFAULT 0,

    CONSTRAINT tenant_settings_value_type_check CHECK (
        value_type IN ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'JSON')
    )
);

-- Outlet-scoped and tenant-wide (outlet_id IS NULL) settings must each be unique;
-- a plain UNIQUE would let duplicate tenant-wide rows through because NULLs
-- never collide.
CREATE UNIQUE INDEX tenant_settings_outlet_key_idx
    ON tenant_settings (tenant_id, outlet_id, setting_key) WHERE outlet_id IS NOT NULL;
CREATE UNIQUE INDEX tenant_settings_tenant_key_idx
    ON tenant_settings (tenant_id, setting_key) WHERE outlet_id IS NULL;


-- ---------------------------------------------------------------------------
-- document_sequences: gapless, human-readable numbering per tenant/type/period.
--
-- Replaces `BILL-{System.currentTimeMillis()}` from the desktop app, which
-- produced unauditable numbers and could collide across two terminals.
-- Allocation is a single `UPDATE ... RETURNING` under a row lock.
-- ---------------------------------------------------------------------------
CREATE TABLE document_sequences (
    id              uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id       uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id       uuid REFERENCES outlets (id) ON DELETE CASCADE,
    document_type   varchar(40) NOT NULL,
    period_key      varchar(12) NOT NULL DEFAULT 'ALL',
    prefix          varchar(24) NOT NULL DEFAULT '',
    suffix          varchar(24) NOT NULL DEFAULT '',
    pad_length      smallint NOT NULL DEFAULT 6,
    next_value      bigint NOT NULL DEFAULT 1,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT document_sequences_type_check CHECK (
        document_type IN (
            'BILL', 'CREDIT_NOTE', 'REFUND', 'REPAIR_ORDER', 'QUOTATION',
            'GRN', 'WHOLESALE_INVOICE', 'SUPPLIER_RETURN', 'PAYMENT_RECEIPT',
            'STOCK_ADJUSTMENT'
        )
    ),
    CONSTRAINT document_sequences_pad_check CHECK (pad_length BETWEEN 1 AND 12),
    CONSTRAINT document_sequences_next_check CHECK (next_value > 0)
);

CREATE UNIQUE INDEX document_sequences_scope_outlet_idx
    ON document_sequences (tenant_id, outlet_id, document_type, period_key)
    WHERE outlet_id IS NOT NULL;
CREATE UNIQUE INDEX document_sequences_scope_tenant_idx
    ON document_sequences (tenant_id, document_type, period_key)
    WHERE outlet_id IS NULL;

COMMENT ON COLUMN document_sequences.period_key IS
    'ALL for a never-resetting counter, or YYYY / YYYY-MM to restart each year or month.';


-- ---------------------------------------------------------------------------
-- tax_rates: the tax engine the desktop app never had.
-- ---------------------------------------------------------------------------
CREATE TABLE tax_rates (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    code                varchar(30) NOT NULL,
    name                varchar(120) NOT NULL,
    rate_percent        numeric(9, 4) NOT NULL,
    is_inclusive        boolean NOT NULL DEFAULT false,
    is_default          boolean NOT NULL DEFAULT false,
    is_active           boolean NOT NULL DEFAULT true,
    effective_from      date NOT NULL DEFAULT CURRENT_DATE,
    effective_to        date,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT tax_rates_tenant_code_key UNIQUE (tenant_id, code),
    CONSTRAINT tax_rates_rate_check CHECK (rate_percent >= 0 AND rate_percent <= 100),
    CONSTRAINT tax_rates_period_check CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE UNIQUE INDEX tax_rates_one_default_idx
    ON tax_rates (tenant_id) WHERE is_default AND is_active;

COMMENT ON COLUMN tax_rates.is_inclusive IS
    'true when the displayed price already contains the tax (common for retail in LK).';


-- ---------------------------------------------------------------------------
-- lookup_values: user-editable dropdown lists. Sourced from repair_types.txt,
-- repair_conditions.txt and borrowed_items.txt in the desktop build.
-- ---------------------------------------------------------------------------
CREATE TABLE lookup_values (
    id              uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id       uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    lookup_type     varchar(40) NOT NULL,
    value           varchar(160) NOT NULL,
    display_order   integer NOT NULL DEFAULT 0,
    is_active       boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         bigint NOT NULL DEFAULT 0,

    CONSTRAINT lookup_values_tenant_type_value_key UNIQUE (tenant_id, lookup_type, value),
    CONSTRAINT lookup_values_type_check CHECK (
        lookup_type IN (
            'REPAIR_TYPE', 'DEVICE_CONDITION', 'BORROWED_ITEM',
            'ITEM_CATEGORY', 'PAYMENT_NOTE', 'REFUND_REASON', 'RETURN_REASON',
            'BANK'
        )
    )
);

CREATE INDEX lookup_values_tenant_type_idx
    ON lookup_values (tenant_id, lookup_type) WHERE is_active;


-- ---------------------------------------------------------------------------
-- updated_at triggers
-- ---------------------------------------------------------------------------
CREATE TRIGGER tenants_set_updated_at BEFORE UPDATE ON tenants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER outlets_set_updated_at BEFORE UPDATE ON outlets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER tenant_settings_set_updated_at BEFORE UPDATE ON tenant_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER document_sequences_set_updated_at BEFORE UPDATE ON document_sequences
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER tax_rates_set_updated_at BEFORE UPDATE ON tax_rates
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER lookup_values_set_updated_at BEFORE UPDATE ON lookup_values
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
