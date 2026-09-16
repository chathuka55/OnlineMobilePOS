-- ============================================================================
-- V14  Carry tenant_id on one-time tokens so accept-invite and reset-password
--      can bind RLS before loading the target user.
--
-- one_time_tokens itself stays off RLS (lookup is by opaque hash), but the
-- users row it points at is tenant-scoped. Without this denormalised column an
-- anonymous caller has no way to establish the correct app.tenant_id.
-- ============================================================================

ALTER TABLE one_time_tokens
    ADD COLUMN tenant_id uuid REFERENCES tenants (id) ON DELETE CASCADE;

COMMENT ON COLUMN one_time_tokens.tenant_id IS
    'Null for platform-operator tokens. Bound into TenantContext before the user row is loaded.';

CREATE INDEX one_time_tokens_tenant_idx ON one_time_tokens (tenant_id)
    WHERE consumed_at IS NULL;
