-- ============================================================
-- Migration: add employee registration fields
-- Run this if you already created the 'smartshift' database from the
-- previous version of smartshift_schema.sql. If you're setting up a
-- fresh database instead, just use the updated smartshift_schema.sql
-- directly - you don't need this file.
-- ============================================================

USE smartshift;

ALTER TABLE employee
    ADD COLUMN bank_account_number VARCHAR(30) NULL AFTER contact_number,
    ADD COLUMN employment_type ENUM('FULL_TIME','PART_TIME') NOT NULL DEFAULT 'FULL_TIME' AFTER contact_number;

-- Optional: backfill your existing n=5 seed employees with sample bank
-- account numbers so the registration screen has something to show.
-- Skip this if you have real data you don't want touched.
UPDATE employee SET bank_account_number = '1234500001' WHERE full_name = 'Aisha';
UPDATE employee SET bank_account_number = '1234500002' WHERE full_name = 'Ben';
UPDATE employee SET bank_account_number = '1234500003' WHERE full_name = 'Chong';
UPDATE employee SET bank_account_number = '1234500004' WHERE full_name = 'Devi';
UPDATE employee SET bank_account_number = '1234500005', employment_type = 'PART_TIME', max_hours_week = 30.00
    WHERE full_name = 'Farid';
