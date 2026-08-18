-- R__undo-V1__alert_dlq.sql
-- Rollback: drops alert_dlq table and its indexes

DROP INDEX IF EXISTS idx_alert_dlq_status;
DROP INDEX IF EXISTS idx_alert_dlq_created_at;
DROP INDEX IF EXISTS idx_alert_dlq_source_event;
DROP TABLE IF EXISTS alert_dlq;
