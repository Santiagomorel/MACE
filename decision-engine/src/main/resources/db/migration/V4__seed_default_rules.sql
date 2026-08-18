-- V4__seed_default_rules.sql
-- Seeds default Drools rule definitions for out-of-the-box decision making
-- Rules are compiled from src/main/resources/rules/*.drl at runtime
-- This migration stores metadata and baseline DRL content for reference

CREATE TABLE IF NOT EXISTS default_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id VARCHAR(255) NOT NULL UNIQUE,
    rule_name VARCHAR(200) NOT NULL,
    drl_content TEXT NOT NULL,
    description TEXT,
    severity_threshold VARCHAR(20) NOT NULL DEFAULT 'HIGH',
    action VARCHAR(50) NOT NULL DEFAULT 'rotate',
    version VARCHAR(50) NOT NULL DEFAULT '1.0.0',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_default_rules_rule_id ON default_rules(rule_id);
CREATE INDEX IF NOT EXISTS idx_default_rules_action ON default_rules(action);

-- Seed default Drools rules (task 14.4)
INSERT INTO default_rules (rule_id, rule_name, drl_content, description, severity_threshold, action, version) VALUES
(
    'verify-rotate',
    'Rotate on verified high-severity alert',
    '
rule "rotate_verified_high_severity"
    salience 100
when
    $alert : Alert(
        status == AlertStatus.VERIFIED,
        detectedSecretType != null,
        payload != null
    )
    $verification : VerificationResult(
        alertId == $alert.id,
        verified == true,
        severity_scope == "HIGH" or severity_scope == "CRITICAL"
    )
then
    $alert.setStatus(AlertStatus.DECIDED);
    $alert.setPayload("DECIDE:rotate");
    update($alert);
end',
    'When an alert is verified as true with high or critical severity, set decision to rotate',
    'HIGH',
    'rotate',
    '1.0.0'
),
(
    'verify-no-action',
    'No action on verified low-severity or unverified alerts',
    '
rule "no_action_unverified"
    salience 50
when
    $alert : Alert(
        status == AlertStatus.VERIFIED,
        $verification : VerificationResult(
            alertId == $alert.id,
            verified == false
        )
    )
then
    $alert.setStatus(AlertStatus.DECIDED);
    $alert.setPayload("DECIDE:no_action");
    update($alert);
end',
    'When an alert verification fails, set decision to no_action',
    'LOW',
    'no_action',
    '1.0.0'
),
(
    'escalate-critical',
    'Escalate critical severity findings',
    '
rule "escalate_critical_findings"
    salience 200
when
    $alert : Alert(
        status == AlertStatus.VERIFIED
    )
    $verification : VerificationResult(
        alertId == $alert.id,
        verified == true,
        severity_scope == "CRITICAL",
        blast_radius == "ENTERPRISE" or blast_radius == "ADMINISTRATOR"
    )
then
    $alert.setStatus(AlertStatus.DECIDED);
    $alert.setPayload("DECIDE:escalate");
    update($alert);
end',
    'When verification confirms critical severity with enterprise/administrator blast radius, escalate immediately',
    'CRITICAL',
    'escalate',
    '1.0.0'
);
