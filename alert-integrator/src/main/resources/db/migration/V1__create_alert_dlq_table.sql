-- V1__create_alert_dlq_table.sql
-- Creates the alert_dlq table matching the AlertDLQEntry JPA entity

CREATE TABLE IF NOT EXISTS alert_dlq (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    raw_payload TEXT,
    error_message VARCHAR(4000),
    source VARCHAR(100),
    source_event_id VARCHAR(255),
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status VARCHAR(20) DEFAULT 'PENDING',
    alert_type VARCHAR(100)
);

-- Indexes on key columns (task 14.2)
CREATE INDEX IF NOT EXISTS idx_alert_dlq_status ON alert_dlq(status);
CREATE INDEX IF NOT EXISTS idx_alert_dlq_created_at ON alert_dlq(created_at);
CREATE INDEX IF NOT EXISTS idx_alert_dlq_source_event ON alert_dlq(source, source_event_id);
