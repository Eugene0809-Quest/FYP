-- ============================================================
-- Migration 011 (FYP2, item 5d-2): a rule row for approved leave.
--
-- The compliance report (ComplianceChecker) can report an assignment on a day of
-- approved leave. constraint_violation.rule_id must point at a constraint_rule
-- row, and there was no row for leave, so this adds it. Data only: no table or
-- column changes, the database stays at 20 tables.
--
-- Safe to run more than once: rule_code is UNIQUE, so a second run just
-- refreshes the same row.
-- Run against a 'smartshift' database that already has migration 010.
-- ============================================================

USE smartshift;

INSERT INTO constraint_rule (rule_code, rule_name, rule_type, weight) VALUES
    ('LEAVE', 'No assignment on a day of approved leave', 'HARD', 1.00)
ON DUPLICATE KEY UPDATE rule_name = VALUES(rule_name);
