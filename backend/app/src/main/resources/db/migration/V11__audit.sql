-- ============================================================================
-- V11  Unified audit trail.
--
-- Collapses BillAuditLog, RepairAuditLog and WholesaleAuditLog - three tables
-- with near-identical columns and three separate viewers - into one
-- append-only, queryable event stream.
-- ============================================================================

CREATE TABLE audit_events (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id           uuid REFERENCES outlets (id) ON DELETE SET NULL,

    entity_type         varchar(40) NOT NULL,
    entity_id           uuid,
    entity_number       varchar(40),
    action              varchar(40) NOT NULL,
    severity            varchar(10) NOT NULL DEFAULT 'INFO',

    actor_id            uuid REFERENCES users (id) ON DELETE SET NULL,
    actor_email         citext,
    actor_name          varchar(160),
    -- Set when a platform admin is acting on a tenant's behalf, so support
    -- actions are never mistaken for the shop's own staff.
    impersonator_id     uuid REFERENCES users (id) ON DELETE SET NULL,

    summary             varchar(400),
    changes             jsonb,
    metadata            jsonb,

    ip_address          inet,
    user_agent          varchar(320),
    device_id           varchar(120),
    request_id          varchar(64),

    occurred_at         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT audit_events_severity_check CHECK (severity IN ('INFO', 'WARN', 'CRITICAL'))
);

CREATE INDEX audit_events_tenant_time_idx ON audit_events (tenant_id, occurred_at DESC);
CREATE INDEX audit_events_entity_idx ON audit_events (entity_type, entity_id)
    WHERE entity_id IS NOT NULL;
CREATE INDEX audit_events_actor_idx ON audit_events (actor_id, occurred_at DESC)
    WHERE actor_id IS NOT NULL;
CREATE INDEX audit_events_action_idx ON audit_events (tenant_id, action, occurred_at DESC);
CREATE INDEX audit_events_critical_idx ON audit_events (tenant_id, occurred_at DESC)
    WHERE severity = 'CRITICAL';
-- Lets the audit viewer filter on arbitrary metadata keys.
CREATE INDEX audit_events_metadata_idx ON audit_events USING gin (metadata jsonb_path_ops);

COMMENT ON COLUMN audit_events.changes IS
    'JSON diff shaped {"field": {"old": ..., "new": ...}}, replacing the old OldValues/NewValues text blobs.';

CREATE TRIGGER audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();


-- ---------------------------------------------------------------------------
-- outbox: reliable side effects (emails, webhooks, receipt rendering) written in
-- the same transaction as the business change, then drained by a poller. Avoids
-- the classic "committed the sale but never sent the receipt" split-brain.
-- ---------------------------------------------------------------------------
CREATE TABLE outbox_messages (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid REFERENCES tenants (id) ON DELETE CASCADE,
    topic               varchar(80) NOT NULL,
    payload             jsonb NOT NULL,
    status              varchar(16) NOT NULL DEFAULT 'PENDING',
    attempts            smallint NOT NULL DEFAULT 0,
    next_attempt_at     timestamptz NOT NULL DEFAULT now(),
    last_error          text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    processed_at        timestamptz,

    CONSTRAINT outbox_messages_status_check CHECK (
        status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')
    )
);

CREATE INDEX outbox_messages_pending_idx ON outbox_messages (next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');
