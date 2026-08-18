-- R__undo-V1__verification_results.sql
-- Rollback: drops verification_results table and its indexes

DROP INDEX IF EXISTS idx_verification_results_alert_id;
DROP INDEX IF EXISTS idx_verification_results_tenant_id;
DROP INDEX IF EXISTS idx_verification_results_credential_type;
DROP TABLE IF EXISTS verification_results;
