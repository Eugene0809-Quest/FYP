-- ============================================================
-- Migration: extend payroll_record for item #4 (public holiday pay)
-- and item #5 (EPF/SOCSO/EIS statutory contributions)
--
-- The original payroll_record only had regular/overtime hours+pay.
-- PayrollRecord.java / PayrollCalculator.java now also produce
-- holiday hours/pay and a full StatutoryBreakdown (employee+employer
-- EPF/SOCSO/EIS, net pay, employer total cost) - none of which had
-- columns to land in. This migration adds them.
--
-- total_pay stays a GENERATED column but is redefined to include
-- holiday_pay (it previously only summed regular_pay + overtime_pay).
-- MySQL requires dropping and re-adding a generated column to change
-- its expression, so that's done explicitly below rather than via
-- MODIFY COLUMN (which MySQL rejects for generated-ness changes).
-- ============================================================

USE smartshift;

ALTER TABLE payroll_record
    ADD COLUMN holiday_hours       DECIMAL(6,2)  NOT NULL DEFAULT 0 AFTER overtime_hours,
    ADD COLUMN holiday_pay         DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER overtime_pay;

ALTER TABLE payroll_record
    DROP COLUMN total_pay;

ALTER TABLE payroll_record
    ADD COLUMN total_pay DECIMAL(10,2)
        GENERATED ALWAYS AS (regular_pay + overtime_pay + holiday_pay) STORED
        AFTER holiday_pay,
    ADD COLUMN employee_epf         DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER total_pay,
    ADD COLUMN employer_epf         DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER employee_epf,
    ADD COLUMN employee_socso       DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER employer_epf,
    ADD COLUMN employer_socso       DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER employee_socso,
    ADD COLUMN employee_eis         DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER employer_socso,
    ADD COLUMN employer_eis         DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER employee_eis,
    ADD COLUMN net_pay              DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER employer_eis,
    ADD COLUMN employer_total_cost  DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER net_pay;
