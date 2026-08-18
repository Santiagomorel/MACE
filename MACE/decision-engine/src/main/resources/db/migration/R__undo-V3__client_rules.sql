-- R__undo-V3__client_rules.sql
-- Rollback: drops client_rules table and its indexes

DROP INDEX IF EXISTS idx_client_rules_version;
DROP INDEX IF EXISTS idx_client_rules_tenant_active;
DROP INDEX IF EXISTS idx_client_rules_tenant;
DROP TABLE IF EXISTS client_rules;
