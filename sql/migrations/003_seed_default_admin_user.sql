-- ============================================================
-- Migration: seed the default admin login
-- Run this if your 'smartshift' database already exists but has
-- no rows in user_account yet (e.g. you rebuilt the schema+seed
-- before the login screen was added).
-- Username: admin   Password: admin123
-- Change this before any real/public use - it's a known demo credential.
-- ============================================================

USE smartshift;

INSERT INTO user_account (username, password_hash, full_name, role)
SELECT 'admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Fong U Kin', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE username = 'admin');
