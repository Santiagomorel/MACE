CREATE TABLE alert_dlq (
    id UUID PRIMARY KEY,
    raw_payload JSONB,
    error_message VARCHAR(4000),
    source VARCHAR(100),
    source_event_id VARCHAR(255),
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    alert_type VARCHAR(100)
);

CREATE INDEX idx_alert_dlq_status ON alert_dlq(status);
CREATE INDEX idx_alert_dlq_created_at ON alert_dlq(created_at);
CREATE INDEX idx_alert_dlq_source_event ON alert_dlq(source, source_event_id);
