-- R__undo-V1__rotation_actions.sql
-- Rollback: drops rotation_actions table and its indexes

DROP INDEX IF EXISTS idx_rotation_actions_alert_id;
DROP INDEX IF EXISTS idx_rotation_actions_status;
DROP INDEX IF EXISTS idx_rotation_actions_credential_type;
DROP TABLE IF EXISTS rotation_actions;
