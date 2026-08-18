-- V1__create_verification_results_table.sql
-- Creates the verification_results table matching the VerificationResult JPA entity

CREATE TABLE IF NOT EXISTS verification_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id UUID NOT NULL,
    verified BOOLEAN NOT NULL,
    reason TEXT,
    severity_scope VARCHAR(100),
    blast_radius VARCHAR(100),
    credential_type VARCHAR(50),
    tenant_id VARCHAR(100),
    provider VARCHAR(100),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Indexes on key columns (task 14.2)
CREATE INDEX IF NOT EXISTS idx_verification_results_alert_id ON verification_results(alert_id);
CREATE INDEX IF NOT EXISTS idx_verification_results_tenant_id ON verification_results(tenant_id);
CREATE INDEX IF NOT EXISTS idx_verification_results_credential_type ON verification_results(credential_type);
