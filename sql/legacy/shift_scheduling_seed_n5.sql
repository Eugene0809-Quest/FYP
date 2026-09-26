USE shift_scheduling;

-- Roles
INSERT INTO role (role_name) VALUES ('Kitchen'), ('Front-of-House');

-- Week plan
INSERT INTO week_plan (week_start_date, status) VALUES ('2026-09-28', 'DRAFT');
SET @wp = LAST_INSERT_ID();

-- 5 employees: 3 Front-of-House, 2 Kitchen
INSERT INTO employee (full_name, role_id, hourly_rate, max_weekly_hours) VALUES
('Aisha',   2, 12.00, 45),
('Ben',     2, 12.50, 45),
('Chong',   2, 11.50, 45),
('Devi',    1, 13.00, 45),
('Farid',   1, 13.50, 45);

-- Shifts for the week: lunch (11-15) and dinner (18-22) daily, Mon-Sun (day 1-7)
-- Front-of-House needs 2 per shift, Kitchen needs 1 per shift
INSERT INTO shift (week_plan_id, day_of_week, start_time, end_time, required_role_id, staff_needed)
SELECT @wp, d, '11:00:00', '15:00:00', 2, 2 FROM (SELECT 1 d UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) days;
INSERT INTO shift (week_plan_id, day_of_week, start_time, end_time, required_role_id, staff_needed)
SELECT @wp, d, '18:00:00', '22:00:00', 2, 2 FROM (SELECT 1 d UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) days;
INSERT INTO shift (week_plan_id, day_of_week, start_time, end_time, required_role_id, staff_needed)
SELECT @wp, d, '11:00:00', '15:00:00', 1, 1 FROM (SELECT 1 d UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) days;
INSERT INTO shift (week_plan_id, day_of_week, start_time, end_time, required_role_id, staff_needed)
SELECT @wp, d, '18:00:00', '22:00:00', 1, 1 FROM (SELECT 1 d UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) days;

-- Availability: everyone available every day for both shift windows except a couple of gaps
-- Aisha (id 1) unavailable Sunday (day 7)
INSERT INTO availability (employee_id, week_plan_id, day_of_week, start_time, end_time)
SELECT 1, @wp, d, '11:00:00', '22:00:00' FROM (SELECT 1 d UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) days;
-- Ben (id 2) available all days
INSERT INTO availability (employee_id, week_plan_id, day_of_week, start_time, end_time)
SELECT 2, @wp, d, '11:00:00', '22:00:00' FROM (SELECT 1 d UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) days;
-- Chong (id 3) available all days
INSERT INTO availability (employee_id, week_plan_id, day_of_week, start_time, end_time)
SELECT 3, @wp, d, '11:00:00', '22:00:00' FROM (SELECT 1 d UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) days;
-- Devi (id 4, Kitchen) available all days
INSERT INTO availability (employee_id, week_plan_id, day_of_week, start_time, end_time)
SELECT 4, @wp, d, '11:00:00', '22:00:00' FROM (SELECT 1 d UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) days;
-- Farid (id 5, Kitchen) unavailable Wednesday (day 3)
INSERT INTO availability (employee_id, week_plan_id, day_of_week, start_time, end_time)
SELECT 5, @wp, d, '11:00:00', '22:00:00' FROM (SELECT 1 d UNION SELECT 2 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) days;

INSERT INTO objective_weight (week_plan_id) VALUES (@wp);

SELECT @wp AS week_plan_id;
