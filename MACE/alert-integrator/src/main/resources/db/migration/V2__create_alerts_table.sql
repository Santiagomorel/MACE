-- V2__create_alerts_table.sql
-- Creates the alerts table matching the Alert JPA entity
-- Required by: alert-integrator module processing pipeline

CREATE TABLE IF NOT EXISTS alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_name VARCHAR(200) NOT NULL,
    credential_type VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payload TEXT,
    raw_payload TEXT,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    state VARCHAR(50)
);

-- Indexes on key columns (task 14.2)
CREATE INDEX IF NOT EXISTS idx_alerts_tenant_id ON alerts(tenant_id);
CREATE INDEX IF NOT EXISTS idx_alerts_status ON alerts(status);
CREATE INDEX IF NOT EXISTS idx_alerts_tenant_status ON alerts(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_alerts_received_at ON alerts(received_at);
