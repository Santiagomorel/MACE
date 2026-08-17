CREATE TABLE verification_results_entity (
    id UUID PRIMARY KEY,
    account_id VARCHAR(255),
    identity_arn VARCHAR(1000),
    status VARCHAR(20) NOT NULL,
    action_matrix JSONB,
    last_used_date VARCHAR(255),
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(2000),
    event_id VARCHAR(255),
    tenant_id VARCHAR(100)
);

CREATE INDEX idx_verification_results_event_id ON verification_results(event_id);
CREATE INDEX idx_verification_results_tenant_id ON verification_results(tenant_id);
CREATE INDEX idx_verification_results_status ON verification_results(status);
CREATE INDEX idx_verification_results_verified_at ON verification_results(verified_at);
CREATE INDEX idx_verification_results_tenant_status ON verification_results(tenant_id, status);
