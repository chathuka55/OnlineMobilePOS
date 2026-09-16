-- ============================================================================
-- V4  Users, roles, granular permissions, sessions and login throttling.
--
-- The desktop app had two hard-coded roles and stored passwords in plaintext
-- (UserDAO.getUserByCredentials compared the raw string). Here passwords are
-- Argon2id hashes and roles expand into individually grantable permissions.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- permissions: platform-defined, immutable catalogue.
-- ---------------------------------------------------------------------------
CREATE TABLE permissions (
    code            varchar(60) PRIMARY KEY,
    module          varchar(40) NOT NULL,
    description     varchar(240) NOT NULL,
    -- Requires a fresh password re-entry, replacing the desktop app's
    -- MainFrame.promptForCredentials() dialog.
    requires_step_up boolean NOT NULL DEFAULT false
);


-- ---------------------------------------------------------------------------
-- roles: system roles are seeded and shared; tenants may add their own.
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    id              uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id       uuid REFERENCES tenants (id) ON DELETE CASCADE,
    code            varchar(40) NOT NULL,
    name            varchar(120) NOT NULL,
    description     varchar(240),
    is_system       boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         bigint NOT NULL DEFAULT 0,

    CONSTRAINT roles_system_has_no_tenant_check CHECK (
        (is_system AND tenant_id IS NULL) OR (NOT is_system AND tenant_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX roles_system_code_idx ON roles (code) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX roles_tenant_code_idx ON roles (tenant_id, code) WHERE tenant_id IS NOT NULL;


CREATE TABLE role_permissions (
    role_id         uuid NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_code varchar(60) NOT NULL REFERENCES permissions (code) ON DELETE CASCADE,

    PRIMARY KEY (role_id, permission_code)
);


-- ---------------------------------------------------------------------------
-- users. Platform staff have tenant_id IS NULL; shop staff always belong to one
-- tenant. Email is unique per tenant so the same person can hold accounts at
-- more than one shop.
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                      uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id               uuid REFERENCES tenants (id) ON DELETE CASCADE,
    email                   citext NOT NULL,
    username                citext,
    password_hash           varchar(255),
    full_name               varchar(160) NOT NULL,
    phone                   varchar(32),
    avatar_object_key       varchar(320),
    is_platform_admin       boolean NOT NULL DEFAULT false,
    status                  varchar(20) NOT NULL DEFAULT 'INVITED',
    -- Numeric till lock code; replaces passcode_config.properties.
    pin_hash                varchar(255),
    last_login_at           timestamptz,
    password_changed_at     timestamptz,
    failed_login_count      smallint NOT NULL DEFAULT 0,
    locked_until            timestamptz,
    deleted_at              timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    version                 bigint NOT NULL DEFAULT 0,

    CONSTRAINT users_status_check CHECK (
        status IN ('INVITED', 'ACTIVE', 'DISABLED', 'LOCKED')
    ),
    CONSTRAINT users_platform_admin_has_no_tenant_check CHECK (
        NOT is_platform_admin OR tenant_id IS NULL
    ),
    -- An ACTIVE account must be able to authenticate.
    CONSTRAINT users_active_needs_password_check CHECK (
        status <> 'ACTIVE' OR password_hash IS NOT NULL
    )
);

CREATE UNIQUE INDEX users_tenant_email_idx ON users (tenant_id, email)
    WHERE tenant_id IS NOT NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX users_platform_email_idx ON users (email)
    WHERE tenant_id IS NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX users_tenant_username_idx ON users (tenant_id, username)
    WHERE username IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX users_tenant_status_idx ON users (tenant_id, status) WHERE deleted_at IS NULL;


CREATE TABLE user_roles (
    user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id     uuid NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    granted_at  timestamptz NOT NULL DEFAULT now(),
    granted_by  uuid REFERENCES users (id) ON DELETE SET NULL,

    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX user_roles_role_idx ON user_roles (role_id);


-- ---------------------------------------------------------------------------
-- refresh_tokens: rotation ledger.
--
-- Live tokens are held in Redis for fast lookup; this table is the durable
-- audit trail and the reuse detector. Presenting an already-rotated token means
-- it leaked, so the whole family is revoked.
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id              uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id         uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tenant_id       uuid REFERENCES tenants (id) ON DELETE CASCADE,
    family_id       uuid NOT NULL,
    token_hash      char(64) NOT NULL,
    device_id       varchar(120),
    device_label    varchar(160),
    user_agent      varchar(320),
    ip_address      inet,
    issued_at       timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL,
    rotated_at      timestamptz,
    revoked_at      timestamptz,
    revoked_reason  varchar(60),

    CONSTRAINT refresh_tokens_hash_key UNIQUE (token_hash)
);

CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id, issued_at DESC);
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_cleanup_idx ON refresh_tokens (expires_at)
    WHERE revoked_at IS NULL;

COMMENT ON COLUMN refresh_tokens.token_hash IS
    'SHA-256 hex of the opaque token. The raw value is never stored.';


-- ---------------------------------------------------------------------------
-- Registered POS terminals. Replaces hardware-fingerprint licensing with a
-- device roster the shop owner controls.
-- ---------------------------------------------------------------------------
CREATE TABLE devices (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id           uuid REFERENCES outlets (id) ON DELETE SET NULL,
    device_id           varchar(120) NOT NULL,
    label               varchar(160) NOT NULL,
    platform            varchar(40),
    app_version         varchar(40),
    printer_name        varchar(120),
    is_approved         boolean NOT NULL DEFAULT false,
    last_seen_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT devices_tenant_device_key UNIQUE (tenant_id, device_id)
);

CREATE INDEX devices_tenant_idx ON devices (tenant_id);


-- ---------------------------------------------------------------------------
-- One-time tokens for invitations, password resets and email verification.
-- ---------------------------------------------------------------------------
CREATE TABLE one_time_tokens (
    id              uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id         uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose         varchar(30) NOT NULL,
    token_hash      char(64) NOT NULL,
    expires_at      timestamptz NOT NULL,
    consumed_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT one_time_tokens_hash_key UNIQUE (token_hash),
    CONSTRAINT one_time_tokens_purpose_check CHECK (
        purpose IN ('INVITE', 'PASSWORD_RESET', 'EMAIL_VERIFY')
    )
);

CREATE INDEX one_time_tokens_user_idx ON one_time_tokens (user_id, purpose)
    WHERE consumed_at IS NULL;


-- ---------------------------------------------------------------------------
-- login_attempts: durable record behind the Redis-backed rate limiter, and the
-- evidence trail for "who tried to get in".
-- ---------------------------------------------------------------------------
CREATE TABLE login_attempts (
    id              uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_slug     citext,
    email           citext NOT NULL,
    ip_address      inet,
    user_agent      varchar(320),
    successful      boolean NOT NULL,
    failure_reason  varchar(60),
    attempted_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX login_attempts_email_time_idx ON login_attempts (email, attempted_at DESC);
CREATE INDEX login_attempts_ip_time_idx ON login_attempts (ip_address, attempted_at DESC);


CREATE TRIGGER roles_set_updated_at BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER users_set_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER devices_set_updated_at BEFORE UPDATE ON devices
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
