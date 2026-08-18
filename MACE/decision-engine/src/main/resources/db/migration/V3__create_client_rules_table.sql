-- V3__create_client_rules_table.sql
CREATE TABLE IF NOT EXISTS client_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    drl_content BYTEA NOT NULL,
    drl_size_bytes INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    manual_override_by_client BOOLEAN DEFAULT false,
    override_by_user VARCHAR(255),
    override_timestamp TIMESTAMP WITH TIME ZONE,
    playbook_id VARCHAR(255),
    active BOOLEAN DEFAULT true
);

CREATE INDEX idx_client_rules_tenant ON client_rules(tenant_id);
CREATE INDEX idx_client_rules_tenant_active ON client_rules(tenant_id, active);
CREATE INDEX idx_client_rules_version ON client_rules(tenant_id, version DESC);
