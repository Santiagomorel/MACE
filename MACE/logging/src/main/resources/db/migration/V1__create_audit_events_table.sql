-- V1__create_audit_events_table.sql

CREATE TABLE audit_events_logging (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    client_id VARCHAR(100),
    alert_id VARCHAR(100),
    phase VARCHAR(50),
    trace_id VARCHAR(100),
    step VARCHAR(100),
    event_data JSONB NOT NULL,
    duration_ms INTEGER,
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_audit_client ON audit_events_logging(client_id);
CREATE INDEX idx_audit_alert ON audit_events_logging(alert_id);
CREATE INDEX idx_audit_created ON audit_events_logging(created_at);
CREATE INDEX idx_audit_data ON audit_events_logging USING GIN(event_data);
