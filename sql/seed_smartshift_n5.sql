-- ============================================================
-- smartshift seed data — n=5 scenario
-- Ported from the earlier shift_scheduling/seed_n5.sql onto the
-- smartshift schema. Same numbers the DAO layer was validated
-- against: 5 employees, 28 shift_definition rows, 33 availability rows.
-- Assumes a freshly created 'smartshift' database (AUTO_INCREMENT
-- starts at 1), so employee ids come out as 1=Aisha .. 5=Farid.
-- ============================================================

USE smartshift;

-- Roles
INSERT INTO role (role_name, description) VALUES
('Kitchen', 'Prepares food'),
('Front-of-House', 'Cashier / customer-facing service');

-- 5 employees: 3 Front-of-House (role 2), 2 Kitchen (role 1)
-- Farid is seeded as PART_TIME to exercise the employment-type -> max-hours
-- feature; everyone else is FULL_TIME (the schema's own default).
INSERT INTO employee (role_id, full_name, bank_account_number, employment_type, hourly_rate, max_hours_week) VALUES
(2, 'Aisha', '1234500001', 'FULL_TIME', 12.00, 45.00),
(2, 'Ben',   '1234500002', 'FULL_TIME', 12.50, 45.00),
(2, 'Chong', '1234500003', 'FULL_TIME', 11.50, 45.00),
(1, 'Devi',  '1234500004', 'FULL_TIME', 13.00, 45.00),
(1, 'Farid', '1234500005', 'PART_TIME', 13.50, 30.00);

-- Shift templates: lunch (11-15) and dinner (18-22) daily, Mon-Sun.
-- Front-of-House needs 2 per shift, Kitchen needs 1 per shift.
-- (shift_definition has no week link - it's a recurring weekly template
-- reused by every schedule run, per the smartshift design.)
INSERT INTO shift_definition (role_id, day_of_week, start_time, end_time, required_staff)
SELECT 2, d, '11:00:00', '15:00:00', 2
FROM (SELECT 'MON' d UNION SELECT 'TUE' UNION SELECT 'WED' UNION SELECT 'THU'
      UNION SELECT 'FRI' UNION SELECT 'SAT' UNION SELECT 'SUN') days;
INSERT INTO shift_definition (role_id, day_of_week, start_time, end_time, required_staff)
SELECT 2, d, '18:00:00', '22:00:00', 2
FROM (SELECT 'MON' d UNION SELECT 'TUE' UNION SELECT 'WED' UNION SELECT 'THU'
      UNION SELECT 'FRI' UNION SELECT 'SAT' UNION SELECT 'SUN') days;
INSERT INTO shift_definition (role_id, day_of_week, start_time, end_time, required_staff)
SELECT 1, d, '11:00:00', '15:00:00', 1
FROM (SELECT 'MON' d UNION SELECT 'TUE' UNION SELECT 'WED' UNION SELECT 'THU'
      UNION SELECT 'FRI' UNION SELECT 'SAT' UNION SELECT 'SUN') days;
INSERT INTO shift_definition (role_id, day_of_week, start_time, end_time, required_staff)
SELECT 1, d, '18:00:00', '22:00:00', 1
FROM (SELECT 'MON' d UNION SELECT 'TUE' UNION SELECT 'WED' UNION SELECT 'THU'
      UNION SELECT 'FRI' UNION SELECT 'SAT' UNION SELECT 'SUN') days;
-- -> 4 combos x 7 days = 28 shift_definition rows

-- Availability: everyone available 11:00-22:00 every day, except:
--   Aisha (id 1)  unavailable Sunday
--   Farid (id 5)  unavailable Wednesday
INSERT INTO availability (employee_id, day_of_week, start_time, end_time)
SELECT 1, d, '11:00:00', '22:00:00'
FROM (SELECT 'MON' d UNION SELECT 'TUE' UNION SELECT 'WED' UNION SELECT 'THU'
      UNION SELECT 'FRI' UNION SELECT 'SAT') days;                          -- 6 rows
INSERT INTO availability (employee_id, day_of_week, start_time, end_time)
SELECT 2, d, '11:00:00', '22:00:00'
FROM (SELECT 'MON' d UNION SELECT 'TUE' UNION SELECT 'WED' UNION SELECT 'THU'
      UNION SELECT 'FRI' UNION SELECT 'SAT' UNION SELECT 'SUN') days;       -- 7 rows
INSERT INTO availability (employee_id, day_of_week, start_time, end_time)
SELECT 3, d, '11:00:00', '22:00:00'
FROM (SELECT 'MON' d UNION SELECT 'TUE' UNION SELECT 'WED' UNION SELECT 'THU'
      UNION SELECT 'FRI' UNION SELECT 'SAT' UNION SELECT 'SUN') days;       -- 7 rows
INSERT INTO availability (employee_id, day_of_week, start_time, end_time)
SELECT 4, d, '11:00:00', '22:00:00'
FROM (SELECT 'MON' d UNION SELECT 'TUE' UNION SELECT 'WED' UNION SELECT 'THU'
      UNION SELECT 'FRI' UNION SELECT 'SAT' UNION SELECT 'SUN') days;       -- 7 rows
INSERT INTO availability (employee_id, day_of_week, start_time, end_time)
SELECT 5, d, '11:00:00', '22:00:00'
FROM (SELECT 'MON' d UNION SELECT 'TUE' UNION SELECT 'THU'
      UNION SELECT 'FRI' UNION SELECT 'SAT' UNION SELECT 'SUN') days;       -- 6 rows
-- -> 6+7+7+7+6 = 33 availability rows

-- One DRAFT schedule run, with its objective weights
INSERT INTO schedule (week_start_date, status) VALUES ('2026-09-28', 'DRAFT');
SET @sched = LAST_INSERT_ID();
INSERT INTO objective_weight (schedule_id) VALUES (@sched);

-- A starter set of constraint rules (hard rules are non-negotiable in the
-- solver regardless of this table; this row set exists so
-- constraint_violation.rule_id has real rows to reference)
INSERT INTO constraint_rule (rule_code, rule_name, rule_type, weight) VALUES
('AVAILABILITY',  'Employee must be available for the shift', 'HARD', 1.00),
('COVERAGE',      'Shift staffing requirement must be met',   'HARD', 1.00),
('NO_OVERLAP',    'No employee double-booked on the same day','HARD', 1.00),
('MAX_HOURS',     'Weekly hour cap (Employment Act 1955)',    'HARD', 1.00),
('ROLE_MATCH',    'Employee role must match shift role',      'HARD', 1.00),
('OVERTIME',      'Minimise overtime hours',                  'SOFT', 1.00),
('WORKLOAD',      'Minimise workload imbalance across staff', 'SOFT', 1.00),
('PREFERENCE',    'Respect employee shift preferences',       'SOFT', 1.00),
('UNFILLED',      'Minimise unfilled shift demand',           'SOFT', 100.00);

-- Default login for the FYP1 demo: username 'admin', password 'admin123'.
-- Change this before any real/public use - it's a known demo credential.
INSERT INTO user_account (username, password_hash, full_name, role) VALUES
('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Fong U Kin', 'ADMIN');

SELECT @sched AS schedule_id;

-- 2026 Malaysia public holidays (previously migration 006).
-- Dates must be re-verified against the official Prime Minister's Department
-- schedule before relying on them; Islamic-calendar dates can still shift.
-- This is one national list: per-state variation is not modelled.
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