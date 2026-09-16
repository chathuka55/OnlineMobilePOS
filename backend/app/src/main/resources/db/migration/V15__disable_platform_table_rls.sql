-- ============================================================================
-- V15  Undo Supabase's automatic RLS enablement on platform/pre-auth tables.
--
-- Supabase runs an automatic hook that force-enables Row Level Security on
-- every table created in the public schema, regardless of migration intent.
-- V12's header comment lists these tables as "deliberately NOT under RLS"
-- because they are either platform-owned or must be readable before a tenant
-- is known (login resolves a slug to a tenant; token refresh learns the
-- tenant from the token row). With RLS force-enabled and no policies, every
-- write to them was silently denied for the app role, breaking signup.
-- ============================================================================
ALTER TABLE tenants                DISABLE ROW LEVEL SECURITY;
ALTER TABLE plans                  DISABLE ROW LEVEL SECURITY;
ALTER TABLE plan_features          DISABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions          DISABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_invoices  DISABLE ROW LEVEL SECURITY;
ALTER TABLE usage_counters         DISABLE ROW LEVEL SECURITY;
ALTER TABLE gateway_events         DISABLE ROW LEVEL SECURITY;
ALTER TABLE permissions            DISABLE ROW LEVEL SECURITY;
ALTER TABLE login_attempts         DISABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_messages        DISABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens         DISABLE ROW LEVEL SECURITY;
ALTER TABLE one_time_tokens        DISABLE ROW LEVEL SECURITY;
ALTER TABLE role_permissions       DISABLE ROW LEVEL SECURITY;
ALTER TABLE user_roles              DISABLE ROW LEVEL SECURITY;
