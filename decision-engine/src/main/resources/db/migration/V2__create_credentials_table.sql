-- V2__create_credentials_table.sql
CREATE TABLE IF NOT EXISTS credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    credential_type VARCHAR(50) NOT NULL,
    provider_arn TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    key_id VARCHAR(50),
    ttl_remaining_seconds BIGINT,
    credential_prefix VARCHAR(10),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_credentials_tenant ON credentials(tenant_id);
CREATE INDEX idx_credentials_key_id ON credentials(key_id);
CREATE INDEX idx_credentials_status ON credentials(status);
