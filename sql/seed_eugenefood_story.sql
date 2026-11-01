-- ============================================================
-- Story seed: EugeneFood (a FICTIONAL single-outlet restaurant in
-- Ipoh, Perak) - FYP2 test company for the SmartShift prototype.
--
-- Responds to the lecturer's feedback that the prototype had no
-- company, no members, no EPF/EIS numbers and no job positions.
--
-- Run AFTER migrations 007 and 008 on a database that already holds the
-- 8 test employees (ids 1 to 8: five Front-of-House, three Kitchen).
-- It can be run again safely: positions use INSERT IGNORE, the company
-- row uses REPLACE, and the employee statements are plain UPDATEs.
--
-- EVERYTHING HERE IS INVENTED. The IC, EPF, SOCSO and EIS values are
-- obvious TEST placeholders so none of them can match a real person.
-- Hourly rates, employment types and bank numbers are not touched.
-- ============================================================

USE smartshift;

-- ------------------------------------------------------------
-- 1. Job positions (rank_level 1 = entry ... 4 = head of department)
-- ------------------------------------------------------------
INSERT IGNORE INTO job_position (role_id, position_name, rank_level, description)
SELECT role_id, 'Service Crew', 1, 'Serves customers and clears tables' FROM role WHERE role_name = 'Front-of-House';
INSERT IGNORE INTO job_position (role_id, position_name, rank_level, description)
SELECT role_id, 'Cashier', 2, 'Takes payments and orders at the counter' FROM role WHERE role_name = 'Front-of-House';
INSERT IGNORE INTO job_position (role_id, position_name, rank_level, description)
SELECT role_id, 'Supervisor', 3, 'Leads the front-of-house team on shift' FROM role WHERE role_name = 'Front-of-House';

INSERT IGNORE INTO job_position (role_id, position_name, rank_level, description)
SELECT role_id, 'Kitchen Helper', 1, 'Prepares ingredients and washes up' FROM role WHERE role_name = 'Kitchen';
INSERT IGNORE INTO job_position (role_id, position_name, rank_level, description)
SELECT role_id, 'Cook', 2, 'Cooks dishes on the line' FROM role WHERE role_name = 'Kitchen';
INSERT IGNORE INTO job_position (role_id, position_name, rank_level, description)
SELECT role_id, 'Second Chef', 3, 'Assists the head chef and runs the line' FROM role WHERE role_name = 'Kitchen';
INSERT IGNORE INTO job_position (role_id, position_name, rank_level, description)
SELECT role_id, 'Head Chef', 4, 'Leads the kitchen and plans the menu' FROM role WHERE role_name = 'Kitchen';

-- ------------------------------------------------------------
-- 2. The company (exactly one row; the opening hours match the
--    11:00-22:00 availability windows and lunch/dinner shifts)
-- ------------------------------------------------------------
REPLACE INTO company
    (company_id, company_name, registration_no, business_type, address,
     contact_number, email, opening_time, closing_time, description)
VALUES
    (1, 'EugeneFood', 'TEST-SSM-0001', 'Restaurant',
     'No. 1, Jalan Contoh, 30000 Ipoh, Perak (fictional address)',
     '05-000 0000', 'info@eugenefood.example', '11:00:00', '22:00:00',
     'A fictional single-outlet restaurant in Ipoh, Perak, used as the test company for the SmartShift prototype. Lunch and dinner service, seven days a week.');

-- ------------------------------------------------------------
-- 3. The members. Each UPDATE joins to job_position on the employee's
--    OWN role, so a position from the wrong department can never be
--    applied: if an employee's role differs from what is assumed here,
--    that UPDATE changes nothing (check the result afterwards).
-- ------------------------------------------------------------

-- Front-of-House
UPDATE employee e
JOIN job_position p ON p.role_id = e.role_id AND p.position_name = 'Supervisor'
SET e.job_position_id = p.job_position_id, e.full_name = 'Aisha binti Rahman',
    e.ic_number = 'TEST-IC-0001', e.epf_number = 'TEST-EPF-0001',
    e.socso_number = 'TEST-SOCSO-0001', e.eis_number = 'TEST-EIS-0001',
    e.date_joined = '2023-03-01'
WHERE e.employee_id = 1;

UPDATE employee e
JOIN job_position p ON p.role_id = e.role_id AND p.position_name = 'Cashier'
SET e.job_position_id = p.job_position_id, e.full_name = 'Ben Lim Jun Hao',
    e.ic_number = 'TEST-IC-0002', e.epf_number = 'TEST-EPF-0002',
    e.socso_number = 'TEST-SOCSO-0002', e.eis_number = 'TEST-EIS-0002',
    e.date_joined = '2024-01-15'
WHERE e.employee_id = 2;

UPDATE employee e
JOIN job_position p ON p.role_id = e.role_id AND p.position_name = 'Cashier'
SET e.job_position_id = p.job_position_id, e.full_name = 'Chong Wei Hong',
    e.ic_number = 'TEST-IC-0003', e.epf_number = 'TEST-EPF-0003',
    e.socso_number = 'TEST-SOCSO-0003', e.eis_number = 'TEST-EIS-0003',
    e.date_joined = '2024-06-10'
WHERE e.employee_id = 3;

-- Eugene keeps the existing name (part-time, Service Crew)
UPDATE employee e
JOIN job_position p ON p.role_id = e.role_id AND p.position_name = 'Service Crew'
SET e.job_position_id = p.job_position_id,
    e.ic_number = 'TEST-IC-0006', e.epf_number = 'TEST-EPF-0006',
    e.socso_number = 'TEST-SOCSO-0006', e.eis_number = 'TEST-EIS-0006',
    e.date_joined = '2025-02-03'
WHERE e.employee_id = 6;

-- Employee 8 was a lowercase duplicate-looking test row ('ali');
-- it becomes a distinct fictional person.
UPDATE employee e
JOIN job_position p ON p.role_id = e.role_id AND p.position_name = 'Service Crew'
SET e.job_position_id = p.job_position_id, e.full_name = 'Nur Aina binti Zakaria',
    e.ic_number = 'TEST-IC-0008', e.epf_number = 'TEST-EPF-0008',
    e.socso_number = 'TEST-SOCSO-0008', e.eis_number = 'TEST-EIS-0008',
    e.date_joined = '2025-05-19'
WHERE e.employee_id = 8;

-- Kitchen
UPDATE employee e
JOIN job_position p ON p.role_id = e.role_id AND p.position_name = 'Head Chef'
SET e.job_position_id = p.job_position_id, e.full_name = 'Devi a/p Krishnan',
    e.ic_number = 'TEST-IC-0004', e.epf_number = 'TEST-EPF-0004',
    e.socso_number = 'TEST-SOCSO-0004', e.eis_number = 'TEST-EIS-0004',
    e.date_joined = '2022-06-01'
WHERE e.employee_id = 4;

UPDATE employee e
JOIN job_position p ON p.role_id = e.role_id AND p.position_name = 'Kitchen Helper'
SET e.job_position_id = p.job_position_id, e.full_name = 'Farid bin Ismail',
    e.ic_number = 'TEST-IC-0005', e.epf_number = 'TEST-EPF-0005',
    e.socso_number = 'TEST-SOCSO-0005', e.eis_number = 'TEST-EIS-0005',
    e.date_joined = '2024-09-02'
WHERE e.employee_id = 5;

UPDATE employee e
JOIN job_position p ON p.role_id = e.role_id AND p.position_name = 'Second Chef'
SET e.job_position_id = p.job_position_id, e.full_name = 'Ali bin Hassan',
    e.ic_number = 'TEST-IC-0007', e.epf_number = 'TEST-EPF-0007',
    e.socso_number = 'TEST-SOCSO-0007', e.eis_number = 'TEST-EIS-0007',
    e.date_joined = '2023-11-20'
WHERE e.employee_id = 7;
