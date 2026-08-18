-- R__undo-V3__audit_events.sql
-- Rollback: drops audit_events table and its indexes

DROP INDEX IF EXISTS idx_audit_events_details;
DROP INDEX IF EXISTS idx_audit_events_tenant_id;
DROP INDEX IF EXISTS idx_audit_events_event_type;
DROP INDEX IF EXISTS idx_audit_events_timestamp;
DROP TABLE IF EXISTS audit_events;
