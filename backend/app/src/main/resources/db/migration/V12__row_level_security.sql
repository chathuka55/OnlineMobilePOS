-- ============================================================================
-- V12  Row-Level Security.
--
-- How isolation works
-- -------------------
-- The API opens a transaction, issues `SET LOCAL app.tenant_id = '<uuid>'` from
-- the verified JWT, and then runs ordinary queries. Application code never
-- writes `WHERE tenant_id = ?`; PostgreSQL appends it. A forgotten filter is
-- therefore a non-event rather than a cross-tenant data leak.
--
-- Two properties make this trustworthy:
--   1. The API connects as ${app_db_user}, which does not own these tables and
--      so cannot bypass a policy.
--   2. current_tenant_id() returns NULL when the variable is unset, and
--      `tenant_id = NULL` is never true, so an unscoped connection sees nothing.
--
-- Deliberately NOT under RLS
-- --------------------------
-- tenants, plans, plan_features, subscriptions, subscription_invoices,
-- usage_counters, gateway_events, permissions, login_attempts, outbox_messages,
-- refresh_tokens, one_time_tokens and the pure join tables. These are either
-- platform-owned or must be readable before a tenant is known: login resolves a
-- slug to a tenant, and token refresh learns the tenant *from* the token row.
-- Tenant-facing reads of them go through a small, explicitly-scoped set of services.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Tables where tenant_id is NOT NULL: a single symmetric policy is enough.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    strict_tables text[] := ARRAY[
        -- tenancy
        'outlets', 'tenant_settings', 'document_sequences', 'tax_rates', 'lookup_values',
        -- identity
        'devices',
        -- crm
        'customers', 'customer_notes',
        -- catalog
        'categories', 'suppliers', 'items', 'item_barcodes',
        'goods_received_notes', 'grn_lines', 'item_serials',
        'supplier_returns', 'supplier_return_lines', 'stock_movements',
        -- sales
        'carts', 'cart_lines', 'bills', 'bill_lines', 'bill_line_serials',
        'payments', 'credit_notes', 'credit_note_redemptions',
        'refunds', 'refund_lines',
        -- repairs
        'repair_orders', 'repair_order_lines', 'repair_order_line_serials',
        'repair_status_history',
        -- wholesale
        'wholesale_invoices', 'wholesale_invoice_lines',
        'wholesale_invoice_line_serials', 'customer_credit_ledger',
        -- quotations
        'quotations', 'quotation_lines'
    ];
    t text;
BEGIN
    FOREACH t IN ARRAY strict_tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format($f$
            CREATE POLICY tenant_isolation ON %I
                FOR ALL
                USING (tenant_id = current_tenant_id())
                WITH CHECK (tenant_id = current_tenant_id())
        $f$, t);
    END LOOP;
END;
$$;


-- ---------------------------------------------------------------------------
-- Tables where tenant_id is nullable because platform-level rows live there too.
--
-- An unset app.tenant_id resolves to NULL, which selects exactly the
-- platform-scoped rows. That is what makes a platform-admin login work without
-- granting any bypass: no tenant context means no tenant data.
-- ---------------------------------------------------------------------------
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON users
    FOR ALL
    USING (tenant_id IS NOT DISTINCT FROM current_tenant_id())
    WITH CHECK (tenant_id IS NOT DISTINCT FROM current_tenant_id());

-- refresh_tokens is deliberately excluded. Token refresh is an unauthenticated call: the
-- tenant is only known *after* the token row is found, so a policy keyed on the current
-- tenant would make refreshing impossible. Isolation instead comes from the lookup key
-- itself, a 384-bit random value stored only as a SHA-256 hash, and from the fact that
-- every query is an exact-hash match.

ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_events
    FOR ALL
    USING (tenant_id IS NOT DISTINCT FROM current_tenant_id())
    WITH CHECK (tenant_id IS NOT DISTINCT FROM current_tenant_id());


-- ---------------------------------------------------------------------------
-- roles: seeded system roles (tenant_id IS NULL) are visible to every tenant,
-- but only tenant-owned rows may be written.
-- ---------------------------------------------------------------------------
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;

CREATE POLICY roles_read ON roles
    FOR SELECT
    USING (tenant_id IS NULL OR tenant_id = current_tenant_id());

CREATE POLICY roles_write ON roles
    FOR INSERT
    WITH CHECK (tenant_id = current_tenant_id());

CREATE POLICY roles_update ON roles
    FOR UPDATE
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

CREATE POLICY roles_delete ON roles
    FOR DELETE
    USING (tenant_id = current_tenant_id() AND NOT is_system);


-- ---------------------------------------------------------------------------
-- one_time_tokens has no tenant_id: an invite is consumed by an anonymous
-- caller who has no tenant context yet, so the opaque token hash is the only
-- credential. Reads are always by exact hash.
-- ---------------------------------------------------------------------------


-- ---------------------------------------------------------------------------
-- Runtime grants. ALTER DEFAULT PRIVILEGES in V1 covers tables created after it,
-- but this makes the grant explicit and re-runnable.
-- ---------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO "${app_db_user}";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO "${app_db_user}";
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO "${app_db_user}";

-- Flyway's own bookkeeping must stay off-limits to the runtime role.
REVOKE ALL ON TABLE flyway_schema_history FROM "${app_db_user}";


-- ---------------------------------------------------------------------------
-- Guard rail: fail the migration if any table carrying a tenant_id column was
-- left unprotected. Without this, adding a table in a future migration and
-- forgetting its policy would silently expose every tenant's rows.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    unprotected text;
BEGIN
    SELECT string_agg(c.relname, ', ' ORDER BY c.relname)
      INTO unprotected
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
      JOIN pg_attribute a ON a.attrelid = c.oid
     WHERE n.nspname = 'public'
       AND c.relkind = 'r'
       AND a.attname = 'tenant_id'
       AND a.attnum > 0
       AND NOT a.attisdropped
       AND NOT c.relrowsecurity
       -- Platform-owned or pre-authentication tables, isolated in application code.
       AND c.relname NOT IN (
           'subscriptions', 'subscription_invoices', 'usage_counters', 'gateway_events',
           'outbox_messages', 'refresh_tokens'
       );

    IF unprotected IS NOT NULL THEN
        RAISE EXCEPTION 'Tables have tenant_id but no Row-Level Security: %', unprotected;
    END IF;
END;
$$;
