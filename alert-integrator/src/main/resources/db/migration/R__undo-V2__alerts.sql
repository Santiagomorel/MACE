-- R__undo-V2__alerts.sql
-- Rollback: drops alerts table and its indexes

DROP INDEX IF EXISTS idx_alerts_tenant_id;
DROP INDEX IF EXISTS idx_alerts_status;
DROP INDEX IF EXISTS idx_alerts_tenant_status;
DROP INDEX IF EXISTS idx_alerts_received_at;
DROP TABLE IF EXISTS alerts;
