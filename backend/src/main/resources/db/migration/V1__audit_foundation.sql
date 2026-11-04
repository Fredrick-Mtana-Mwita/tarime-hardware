-- Foundation only. Business entities are added with their feature migrations.
CREATE TABLE app.audit_events (
    event_id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_id UUID,
    actor_type VARCHAR(16) NOT NULL CHECK (actor_type IN ('SYSTEM', 'CUSTOMER', 'STAFF')),
    event_type VARCHAR(100) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    target_reference VARCHAR(200) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(metadata) = 'object')
);
CREATE INDEX audit_events_target_time_idx
    ON app.audit_events (target_type, target_reference, occurred_at DESC);
CREATE INDEX audit_events_actor_time_idx ON app.audit_events (actor_id, occurred_at DESC);

CREATE FUNCTION app.reject_audit_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit events are append-only' USING ERRCODE = '23000';
END;
$$;
CREATE TRIGGER audit_events_append_only
    BEFORE UPDATE OR DELETE ON app.audit_events
    FOR EACH ROW EXECUTE FUNCTION app.reject_audit_mutation();
COMMENT ON TABLE app.audit_events IS 'Append-only business audit events; never include credentials or unnecessary personal data.';
