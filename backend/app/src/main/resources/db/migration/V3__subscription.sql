-- ============================================================================
-- V3  The SaaS commercial layer: plans, entitlements, subscriptions, invoices
--     and gateway webhook bookkeeping.
--
-- This is entirely new. The desktop product was licensed with a hardware
-- fingerprint (licensing/HardwareFingerprint.java) and a 14-day trial recorded
-- in db/trial_info.properties; both are replaced by subscription state here.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- plans: catalogue rows owned by the platform, not by any tenant.
-- ---------------------------------------------------------------------------
CREATE TABLE plans (
    id                      uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    code                    varchar(40) NOT NULL,
    name                    varchar(120) NOT NULL,
    description             text,
    currency                char(3) NOT NULL DEFAULT 'LKR',
    price_monthly           numeric(14, 2) NOT NULL DEFAULT 0,
    price_yearly            numeric(14, 2) NOT NULL DEFAULT 0,
    trial_days              smallint NOT NULL DEFAULT 14,
    max_users               integer,
    max_outlets             integer,
    max_items               integer,
    max_monthly_bills       integer,
    is_public               boolean NOT NULL DEFAULT true,
    is_active               boolean NOT NULL DEFAULT true,
    display_order           integer NOT NULL DEFAULT 0,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    version                 bigint NOT NULL DEFAULT 0,

    CONSTRAINT plans_code_key UNIQUE (code),
    CONSTRAINT plans_price_check CHECK (price_monthly >= 0 AND price_yearly >= 0)
);

COMMENT ON COLUMN plans.max_users IS 'NULL means unlimited. Applies to all quota columns.';


-- ---------------------------------------------------------------------------
-- plan_features: which modules a plan unlocks. Enforced at the API boundary by
-- the @RequiresFeature annotation.
-- ---------------------------------------------------------------------------
CREATE TABLE plan_features (
    id              uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    plan_id         uuid NOT NULL REFERENCES plans (id) ON DELETE CASCADE,
    feature_code    varchar(60) NOT NULL,
    is_enabled      boolean NOT NULL DEFAULT true,
    limit_value     integer,

    CONSTRAINT plan_features_plan_feature_key UNIQUE (plan_id, feature_code),
    CONSTRAINT plan_features_code_check CHECK (
        feature_code IN (
            'RETAIL_BILLING', 'INVENTORY', 'SERIAL_TRACKING', 'GRN',
            'REPAIRS', 'WHOLESALE', 'QUOTATIONS', 'CREDIT_NOTES',
            'MULTI_OUTLET', 'ADVANCED_REPORTS', 'JASPER_EXPORT',
            'AUDIT_TRAIL', 'API_ACCESS', 'THERMAL_PRINTING', 'BARCODE_LABELS'
        )
    )
);

CREATE INDEX plan_features_plan_idx ON plan_features (plan_id);


-- ---------------------------------------------------------------------------
-- subscriptions: exactly one current subscription per tenant.
-- ---------------------------------------------------------------------------
CREATE TABLE subscriptions (
    id                      uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id               uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    plan_id                 uuid NOT NULL REFERENCES plans (id),
    status                  varchar(20) NOT NULL DEFAULT 'TRIALING',
    billing_cycle           varchar(10) NOT NULL DEFAULT 'MONTHLY',
    currency                char(3) NOT NULL DEFAULT 'LKR',
    unit_amount             numeric(14, 2) NOT NULL DEFAULT 0,
    trial_started_at        timestamptz,
    trial_ends_at           timestamptz,
    current_period_start    timestamptz,
    current_period_end      timestamptz,
    grace_period_ends_at    timestamptz,
    cancel_at_period_end    boolean NOT NULL DEFAULT false,
    cancelled_at            timestamptz,
    gateway                 varchar(20),
    gateway_customer_ref    varchar(120),
    gateway_subscription_ref varchar(120),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    version                 bigint NOT NULL DEFAULT 0,

    CONSTRAINT subscriptions_tenant_key UNIQUE (tenant_id),
    CONSTRAINT subscriptions_status_check CHECK (
        status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'GRACE', 'SUSPENDED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT subscriptions_cycle_check CHECK (billing_cycle IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT subscriptions_gateway_check CHECK (
        gateway IS NULL OR gateway IN ('STRIPE', 'PAYHERE', 'MANUAL')
    )
);

CREATE INDEX subscriptions_status_idx ON subscriptions (status);
-- Drives the nightly job that expires trials and closes grace periods.
CREATE INDEX subscriptions_trial_ends_idx ON subscriptions (trial_ends_at)
    WHERE status = 'TRIALING';
CREATE INDEX subscriptions_period_end_idx ON subscriptions (current_period_end)
    WHERE status IN ('ACTIVE', 'PAST_DUE', 'GRACE');


-- ---------------------------------------------------------------------------
-- subscription_invoices: what the platform charged the tenant. Distinct from
-- the tenant's own sales invoices (`bills`).
-- ---------------------------------------------------------------------------
CREATE TABLE subscription_invoices (
    id                      uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id               uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    subscription_id         uuid NOT NULL REFERENCES subscriptions (id) ON DELETE CASCADE,
    invoice_number          varchar(40) NOT NULL,
    status                  varchar(20) NOT NULL DEFAULT 'DRAFT',
    currency                char(3) NOT NULL DEFAULT 'LKR',
    subtotal                numeric(14, 2) NOT NULL DEFAULT 0,
    tax_amount              numeric(14, 2) NOT NULL DEFAULT 0,
    total                   numeric(14, 2) NOT NULL DEFAULT 0,
    amount_paid             numeric(14, 2) NOT NULL DEFAULT 0,
    period_start            timestamptz,
    period_end              timestamptz,
    issued_at               timestamptz,
    due_at                  timestamptz,
    paid_at                 timestamptz,
    gateway                 varchar(20),
    gateway_invoice_ref     varchar(120),
    gateway_payment_ref     varchar(120),
    failure_reason          text,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    version                 bigint NOT NULL DEFAULT 0,

    CONSTRAINT subscription_invoices_number_key UNIQUE (invoice_number),
    CONSTRAINT subscription_invoices_status_check CHECK (
        status IN ('DRAFT', 'OPEN', 'PAID', 'PARTIALLY_PAID', 'VOID', 'UNCOLLECTIBLE')
    ),
    CONSTRAINT subscription_invoices_amounts_check CHECK (
        subtotal >= 0 AND tax_amount >= 0 AND total >= 0 AND amount_paid >= 0
    )
);

CREATE INDEX subscription_invoices_tenant_idx ON subscription_invoices (tenant_id, issued_at DESC);
CREATE INDEX subscription_invoices_status_idx ON subscription_invoices (status)
    WHERE status IN ('OPEN', 'PARTIALLY_PAID');


-- ---------------------------------------------------------------------------
-- usage_counters: rolling per-period usage checked against plan quotas.
-- ---------------------------------------------------------------------------
CREATE TABLE usage_counters (
    id              uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id       uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    metric          varchar(40) NOT NULL,
    period_key      varchar(12) NOT NULL,
    counter_value   bigint NOT NULL DEFAULT 0,
    updated_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT usage_counters_scope_key UNIQUE (tenant_id, metric, period_key),
    CONSTRAINT usage_counters_metric_check CHECK (
        metric IN ('BILLS', 'ITEMS', 'USERS', 'OUTLETS', 'API_CALLS', 'STORAGE_BYTES')
    )
);


-- ---------------------------------------------------------------------------
-- gateway_events: raw webhook payloads, deduplicated by the gateway's own event
-- id so a redelivery can never double-charge or double-activate.
-- ---------------------------------------------------------------------------
CREATE TABLE gateway_events (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    gateway             varchar(20) NOT NULL,
    external_event_id   varchar(160) NOT NULL,
    event_type          varchar(80) NOT NULL,
    tenant_id           uuid REFERENCES tenants (id) ON DELETE SET NULL,
    payload             jsonb NOT NULL,
    processed_at        timestamptz,
    processing_error    text,
    received_at         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT gateway_events_external_key UNIQUE (gateway, external_event_id)
);

CREATE INDEX gateway_events_unprocessed_idx ON gateway_events (received_at)
    WHERE processed_at IS NULL;


CREATE TRIGGER plans_set_updated_at BEFORE UPDATE ON plans
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER subscriptions_set_updated_at BEFORE UPDATE ON subscriptions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER subscription_invoices_set_updated_at BEFORE UPDATE ON subscription_invoices
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
