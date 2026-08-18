-- R__undo-V2__credentials.sql
-- Rollback: drops credentials table and its indexes

DROP INDEX IF EXISTS idx_credentials_tenant_status;
DROP INDEX IF EXISTS idx_credentials_tenant;
DROP INDEX IF EXISTS idx_credentials_key_id;
DROP INDEX IF EXISTS idx_credentials_status;
DROP TABLE IF EXISTS credentials;
