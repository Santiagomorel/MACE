-- V3__create_audit_events_table.sql
-- Creates the audit_events table matching the AuditEvent JPA entity
-- Cross-cutting: used by alert-integrator, verification-engine, decision-engine, action-executor

CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    alert_id UUID,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    details JSONB,
    user_id VARCHAR(100)
);

-- Indexes on key columns (task 14.2)
CREATE INDEX IF NOT EXISTS idx_audit_events_tenant_id ON audit_events(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_event_type ON audit_events(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_events_timestamp ON audit_events(timestamp);

-- JSONB GIN index on details for flexible querying (task 14.3)
CREATE INDEX IF NOT EXISTS idx_audit_events_details ON audit_events USING GIN(details);
