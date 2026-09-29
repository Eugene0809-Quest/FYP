-- ============================================================
-- Migration: add the public_holiday table (item #4)
-- PublicHolidayDao/PublicHolidayPane have been reading/writing this
-- table already, but it was never added to smartshift_schema.sql -
-- meaning it only exists locally, not in the versioned schema. Run
-- this once against your existing 'smartshift' database.
-- ============================================================

USE smartshift;

CREATE TABLE IF NOT EXISTS public_holiday (
    holiday_id      INT AUTO_INCREMENT PRIMARY KEY,
    holiday_date    DATE NOT NULL UNIQUE,
    description     VARCHAR(255) NOT NULL
) ENGINE=InnoDB;
