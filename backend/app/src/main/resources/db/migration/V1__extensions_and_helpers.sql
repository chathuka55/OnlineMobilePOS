-- ============================================================================
-- V1  Extensions, shared helper functions, and the runtime application role.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;      -- fuzzy item/customer search
CREATE EXTENSION IF NOT EXISTS btree_gin;
CREATE EXTENSION IF NOT EXISTS citext;       -- case-insensitive emails/usernames


-- ---------------------------------------------------------------------------
-- UUIDv7: time-ordered UUIDs. Keeps B-tree inserts append-only (unlike v4) and
-- lets a POS terminal mint IDs offline without coordinating with the server.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION uuid_generate_v7()
RETURNS uuid
LANGUAGE plpgsql
VOLATILE
AS $$
BEGIN
    RETURN encode(
        set_bit(
            set_bit(
                overlay(
                    uuid_send(gen_random_uuid())
                    PLACING substring(
                        int8send(floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint)
                        FROM 3
                    )
                    FROM 1 FOR 6
                ),
                52, 1
            ),
            53, 1
        ),
        'hex'
    )::uuid;
END;
$$;

COMMENT ON FUNCTION uuid_generate_v7() IS
    'RFC 9562 UUID version 7 (48-bit millisecond timestamp prefix + random tail).';


-- ---------------------------------------------------------------------------
-- Tenant resolution used by every Row-Level Security policy.
-- `missing_ok = true` means an unset variable yields NULL, and because
-- `tenant_id = NULL` is never true the policies fail closed.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION current_tenant_id()
RETURNS uuid
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid;
$$;

COMMENT ON FUNCTION current_tenant_id() IS
    'Tenant scope for the current transaction, set by the API via SET LOCAL app.tenant_id.';


-- ---------------------------------------------------------------------------
-- Keeps updated_at honest even for writes that bypass JPA (SQL migrations,
-- maintenance scripts, bulk imports).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;


-- ---------------------------------------------------------------------------
-- Blocks UPDATE/DELETE on append-only tables (the stock ledger and audit log).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION forbid_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; % is not permitted',
        TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;


-- ---------------------------------------------------------------------------
-- Least-privilege runtime role.
--
-- Migrations run as the schema owner (which implicitly bypasses RLS), while the
-- API connects as this non-owning role so tenant policies are always enforced.
-- Granting a superuser or the table owner to the API would silently disable
-- every isolation policy in V12.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_db_user}') THEN
        EXECUTE format(
            'CREATE ROLE %I LOGIN PASSWORD %L',
            '${app_db_user}', '${app_db_password}'
        );
    ELSE
        EXECUTE format(
            'ALTER ROLE %I LOGIN PASSWORD %L',
            '${app_db_user}', '${app_db_password}'
        );
    END IF;
END;
$$;

GRANT CONNECT ON DATABASE "${db_name}" TO "${app_db_user}";
GRANT USAGE ON SCHEMA public TO "${app_db_user}";

-- Applies to everything created by the migrations that follow.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "${app_db_user}";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO "${app_db_user}";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO "${app_db_user}";
