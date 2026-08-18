-- V1__create_rotation_actions_table.sql
-- Creates the rotation_actions table matching the RotationAction JPA entity
-- Required by: action-executor module rotation pipeline

CREATE TABLE IF NOT EXISTS rotation_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id UUID NOT NULL,
    credential_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider VARCHAR(100),
    attempts INTEGER DEFAULT 0,
    timeout BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Indexes on key columns (task 14.2)
CREATE INDEX IF NOT EXISTS idx_rotation_actions_alert_id ON rotation_actions(alert_id);
CREATE INDEX IF NOT EXISTS idx_rotation_actions_status ON rotation_actions(status);
CREATE INDEX IF NOT EXISTS idx_rotation_actions_credential_type ON rotation_actions(credential_type);
