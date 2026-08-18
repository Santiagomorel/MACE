-- R__undo-V4__default_rules.sql
-- Rollback: drops default_rules table and its indexes

DROP INDEX IF EXISTS idx_default_rules_rule_id;
DROP INDEX IF EXISTS idx_default_rules_action;
DROP TABLE IF EXISTS default_rules;
