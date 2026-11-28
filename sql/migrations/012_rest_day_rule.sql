-- ============================================================
-- Migration 012 (FYP2, item 5e): a rule row for the weekly rest day.
--
-- The solver now guarantees at least one rest day a week (Employment Act 1955,
-- s. 59) and the compliance audit can report a roster that breaks it.
-- constraint_violation.rule_id must point at a constraint_rule row, so this adds
-- the REST_DAY rule. Data only: no table or column changes, still 20 tables.
--
-- Safe to run more than once (rule_code is UNIQUE).
-- Run against a 'smartshift' database that already has migration 011.
-- ============================================================

USE smartshift;

INSERT INTO constraint_rule (rule_code, rule_name, rule_type, weight) VALUES
    ('REST_DAY', 'At least one rest day per week (nobody rostered on all seven days)', 'HARD', 1.00)
ON DUPLICATE KEY UPDATE rule_name = VALUES(rule_name);
