-- ============================================================
-- Migration: add public_holiday table
-- Run this if your 'smartshift' database already exists. If you're
-- setting up a fresh database instead, just use the updated
-- smartshift_schema.sql directly - you don't need this file.
--
-- Supports supervisor-requested item #4: public holiday calculation.
-- Work on a gazetted public holiday is paid at a premium rate under the
-- Employment Act 1955; this FYP1 build simplifies that to a flat 2x
-- hourly rate for the hours worked that day - see PayrollCalculator.java
-- for the exact formula and the README's "Known simplifications" section
-- for what real-world nuance this leaves out.
-- ============================================================

USE smartshift;

CREATE TABLE IF NOT EXISTS public_holiday (
    holiday_id    INT AUTO_INCREMENT PRIMARY KEY,
    holiday_date  DATE NOT NULL UNIQUE,
    description   VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

-- Malaysia's national public holidays for 2026, cross-checked across
-- several independent sources at the time this was written (fixed-date
-- ones are certain; Islamic-calendar ones are per JAKIM's 2026 gazette and
-- can still shift slightly pending official moon-sighting confirmation).
-- NOTE (flag for the report's scope section): a few of these are not
-- observed in every state (e.g. New Year's Day is skipped in Johor, Kedah,
-- Kelantan, Perlis and Terengganu) - this single national list doesn't
-- model per-state variation, which is a simplification worth naming
-- alongside the other "Known simplifications". Re-verify against the
-- official Prime Minister's Department gazette before relying on this for
-- anything beyond a class demo.
INSERT IGNORE INTO public_holiday (holiday_date, description) VALUES
('2026-01-01', 'New Year''s Day'),
('2026-02-17', 'Chinese New Year (Day 1)'),
('2026-02-18', 'Chinese New Year (Day 2)'),
('2026-03-21', 'Hari Raya Aidilfitri (Day 1)'),
('2026-03-22', 'Hari Raya Aidilfitri (Day 2)'),
('2026-05-01', 'Labour Day'),
('2026-05-27', 'Hari Raya Haji'),
('2026-05-31', 'Wesak Day'),
('2026-06-01', 'Yang di-Pertuan Agong''s Birthday'),
('2026-06-17', 'Awal Muharram'),
('2026-08-25', 'Maulidur Rasul'),
('2026-08-31', 'Merdeka Day'),
('2026-09-16', 'Malaysia Day'),
('2026-11-08', 'Deepavali'),
('2026-12-25', 'Christmas Day');
