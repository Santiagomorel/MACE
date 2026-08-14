-- V1__create_playbooks_table.sql
CREATE TABLE IF NOT EXISTS playbooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playbook_id VARCHAR(255) NOT NULL UNIQUE,
    version VARCHAR(50) NOT NULL,
    content JSONB NOT NULL,
    provider VARCHAR(50) NOT NULL DEFAULT 'aws',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_playbooks_id ON playbooks(playbook_id);

-- Seed the 4 global AWS playbooks
INSERT INTO playbooks (playbook_id, version, content, provider) VALUES
(
    'aws-access-key-exposed',
    '1.0.0',
    '{
        "playbook_id": "aws-access-key-exposed",
        "version": "1.0.0",
        "credential_types": ["AKIA"],
        "severity_floor": {
            "s3_full_access": "CRITICO",
            "s3_read_only": "ALTO",
            "iam_modify": "CRITICO",
            "ec2_instance_control": "CRITICO",
            "cloudwatch_read": "MEDIA",
            "nothing_active": "BAJO"
        },
        "auto_rotate": {"enabled": true, "max_window_mins": 15},
        "can_lower_floor": false
    }'::jsonb,
    'aws'
),
(
    'aws-session-token-leaked',
    '1.0.0',
    '{
        "playbook_id": "aws-session-token-leaked",
        "version": "1.0.0",
        "credential_types": ["ASIA"],
        "severity_floor": {
            "assumed_role_admin_full_access": "CRITICO",
            "assumed_role_s3_read_write": "ALTO",
            "assumed_role_ec2_manage": "ALTO",
            "assumed_role_read_only": "MEDIA",
            "expired_within_1h": "BAJO"
        },
        "auto_rotate": {"enabled": false, "max_window_mins": null},
        "can_lower_floor": false
    }'::jsonb,
    'aws'
),
(
    'aws-root-credentials-exposed',
    '1.0.0',
    '{
        "playbook_id": "aws-root-credentials-exposed",
        "version": "1.0.0",
        "credential_types": ["ROOT_"],
        "severity_floor": {
            "ec2_active": "CRITICO",
            "s3_bucket_active": "CRITICO",
            "iam_role_attached": "CRITICO",
            "any_resource_active": "CRITICO",
            "no_resources_active": "CRITICO"
        },
        "auto_rotate": {"enabled": true, "max_window_mins": 15},
        "can_lower_floor": false
    }'::jsonb,
    'aws'
),
(
    'aws-iam-role-assumption-abuse',
    '1.0.0',
    '{
        "playbook_id": "aws-iam-role-assumption-abuse",
        "version": "1.0.0",
        "credential_types": ["AKIA", "ASIA"],
        "severity_floor": {
            "cross_account_assume_untrusted_trust": "CRITICO",
            "admin_role_assumed_from_regular_user": "CRITICO",
            "sensitive_data_role_from_external_entity": "CRITICO",
            "regular_role_from_internal_source_verified": "MEDIA",
            "orphaned_role_no_attached_policies": "BAJO"
        },
        "auto_rotate": {"enabled": false, "max_window_mins": null},
        "can_lower_floor": false
    }'::jsonb,
    'aws'
);
