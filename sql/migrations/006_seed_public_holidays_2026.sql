-- ============================================================
-- Seed data: Malaysia's national public holidays for 2026
-- Restores the data that was overwritten when migration 004 was
-- replaced with a simpler version (no seed rows). Cross-checked
-- across several sources at the time this was written; fixed-date
-- ones are certain, Islamic-calendar ones follow JAKIM's 2026 gazette
-- and can still shift slightly pending official moon-sighting
-- confirmation.
--
-- NOTE (flag for the report's scope section): this is a single
-- national list - it doesn't model per-state variation (e.g. New
-- Year's Day is skipped in Johor, Kedah, Kelantan, Perlis and
-- Terengganu). Worth naming alongside the other "Known
-- simplifications". Re-verify against the official Prime Minister's
-- Department gazette before relying on this for anything beyond a
-- class demo.
-- ============================================================

USE smartshift;

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
