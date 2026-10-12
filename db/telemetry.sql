-- For a fresh database only; compatible with the original Alembic schema.
CREATE TABLE IF NOT EXISTS telemetry (
    telemetry_id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    event_name VARCHAR(100) NOT NULL,
    event_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_telemetry_user_id ON telemetry (user_id);
CREATE INDEX IF NOT EXISTS ix_telemetry_event_name ON telemetry (event_name);
CREATE INDEX IF NOT EXISTS ix_telemetry_telemetry_id ON telemetry (telemetry_id);
