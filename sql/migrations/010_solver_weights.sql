-- ============================================================
-- Migration 010 (FYP2, item 5c-2): solver weights live in the database.
--
-- constraint_rule.weight now holds the objective weights the solver uses, in
-- ringgit (see SolverWeights.java):
--   OVERTIME     RM per overtime hour                         (default 100)
--   WORKLOAD     RM per hour of imbalance within a role       (default 5)
--   PREFERENCE   RM per preference level                      (default 2)
--   UNFILLED     RM per unfilled position                     (default 10000)
--   LABOUR_COST  multiplier on the wage bill, 1.00 = real pay (default 1)
-- The presence of the LABOUR_COST row tells the program this migration has been
-- applied; without it the program uses its built-in defaults.
--
-- objective_weight (one row per saved roster) records the weights that were
-- used to generate that roster, so a saved roster can be explained later.
--
-- No new table: the database stays at 20 tables.
-- Run ONCE against a 'smartshift' database that already has migration 009.
-- Not safe to run twice (lambda_cost would already exist).
-- ============================================================

USE smartshift;

-- 1. Room for RM10,000.00 (the old DECIMAL(6,2) stops at 9,999.99).
ALTER TABLE constraint_rule
    MODIFY weight DECIMAL(10,2) NOT NULL DEFAULT 1.00;

-- 2. The five soft rules, with their meaning and unit. Existing rows are updated
--    (their old values 1, 1, 1, 100 were never read by the program).
INSERT INTO constraint_rule (rule_code, rule_name, rule_type, weight) VALUES
    ('OVERTIME',    'Overtime penalty (RM per overtime hour)',                          'SOFT', 100.00),
    ('WORKLOAD',    'Workload imbalance within a role (RM per hour of difference)',     'SOFT',   5.00),
    ('PREFERENCE',  'Value of one shift preference level (RM per level)',               'SOFT',   2.00),
    ('UNFILLED',    'Price of one unfilled position (RM)',                              'SOFT', 10000.00),
    ('LABOUR_COST', 'Importance of the wage bill (1.00 = real pay, 0 = ignore pay)',    'SOFT',   1.00)
ON DUPLICATE KEY UPDATE rule_name = VALUES(rule_name), weight = VALUES(weight);

-- 3. objective_weight: same units, room for 10000.00, and the cost multiplier.
ALTER TABLE objective_weight
    MODIFY lambda_overtime   DECIMAL(10,2) NOT NULL DEFAULT 100.00,
    MODIFY lambda_workload   DECIMAL(10,2) NOT NULL DEFAULT 5.00,
    MODIFY lambda_preference DECIMAL(10,2) NOT NULL DEFAULT 2.00,
    MODIFY lambda_unfilled   DECIMAL(10,2) NOT NULL DEFAULT 10000.00,
    ADD COLUMN lambda_cost   DECIMAL(10,2) NOT NULL DEFAULT 1.00 AFTER lambda_unfilled;

-- Rows that still hold the untouched old seed defaults (1, 1, 1, 100) get the new
-- defaults, so they do not claim to be "RM1 per hour". Any other row is left alone.
UPDATE objective_weight
   SET lambda_overtime = 100.00, lambda_workload = 5.00,
       lambda_preference = 2.00, lambda_unfilled = 10000.00
 WHERE lambda_overtime = 1.00 AND lambda_workload = 1.00
   AND lambda_preference = 1.00 AND lambda_unfilled = 100.00;
