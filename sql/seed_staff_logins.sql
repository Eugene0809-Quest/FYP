-- ============================================================
-- Demo staff logins for the EugeneFood test company (FYP2).
-- One STAFF account per member, linked to the employee record.
--
--   username: first name in lower case (nuraina for Nur Aina)
--   password: staff123   (the same demo password for every account)
--
-- These are KNOWN DEMO CREDENTIALS for the prototype. Change them
-- before any real use. The password is stored as an unsalted SHA-256
-- hash, like the admin account (see the report's security note).
--
-- Run AFTER seed_eugenefood_story.sql (it relies on employee ids 1 to 8).
-- INSERT IGNORE makes it safe to run twice: a username or employee link
-- that already exists is skipped. To remove these accounts again:
--   DELETE FROM user_account WHERE role = 'STAFF';
-- ============================================================

USE smartshift;

INSERT IGNORE INTO user_account (username, password_hash, full_name, role, employee_id)
SELECT 'aisha', '10176e7b7b24d317acfcf8d2064cfd2f24e154f7b5a96603077d5ef813d6a6b6', full_name, 'STAFF', employee_id
FROM employee WHERE employee_id = 1;
INSERT IGNORE INTO user_account (username, password_hash, full_name, role, employee_id)
SELECT 'ben', '10176e7b7b24d317acfcf8d2064cfd2f24e154f7b5a96603077d5ef813d6a6b6', full_name, 'STAFF', employee_id
FROM employee WHERE employee_id = 2;
INSERT IGNORE INTO user_account (username, password_hash, full_name, role, employee_id)
SELECT 'chong', '10176e7b7b24d317acfcf8d2064cfd2f24e154f7b5a96603077d5ef813d6a6b6', full_name, 'STAFF', employee_id
FROM employee WHERE employee_id = 3;
INSERT IGNORE INTO user_account (username, password_hash, full_name, role, employee_id)
SELECT 'devi', '10176e7b7b24d317acfcf8d2064cfd2f24e154f7b5a96603077d5ef813d6a6b6', full_name, 'STAFF', employee_id
FROM employee WHERE employee_id = 4;
INSERT IGNORE INTO user_account (username, password_hash, full_name, role, employee_id)
SELECT 'farid', '10176e7b7b24d317acfcf8d2064cfd2f24e154f7b5a96603077d5ef813d6a6b6', full_name, 'STAFF', employee_id
FROM employee WHERE employee_id = 5;
INSERT IGNORE INTO user_account (username, password_hash, full_name, role, employee_id)
SELECT 'eugene', '10176e7b7b24d317acfcf8d2064cfd2f24e154f7b5a96603077d5ef813d6a6b6', full_name, 'STAFF', employee_id
FROM employee WHERE employee_id = 6;
INSERT IGNORE INTO user_account (username, password_hash, full_name, role, employee_id)
SELECT 'ali', '10176e7b7b24d317acfcf8d2064cfd2f24e154f7b5a96603077d5ef813d6a6b6', full_name, 'STAFF', employee_id
FROM employee WHERE employee_id = 7;
INSERT IGNORE INTO user_account (username, password_hash, full_name, role, employee_id)
SELECT 'nuraina', '10176e7b7b24d317acfcf8d2064cfd2f24e154f7b5a96603077d5ef813d6a6b6', full_name, 'STAFF', employee_id
FROM employee WHERE employee_id = 8;
