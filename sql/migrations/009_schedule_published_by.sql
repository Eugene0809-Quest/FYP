-- ============================================================
-- Migration 009 (FYP2, item 4b): record who published a roster and when.
--
-- schedule.published_by  = the user_account that clicked "Approve & Publish"
-- schedule.published_at  = when they did it
--
-- Both are NULL unless the schedule is currently published (status
-- APPROVED). Unpublishing, or being replaced by another schedule for the
-- same week, sets them back to NULL, so they always describe the CURRENT
-- publication. A roster that was already published before this migration
-- keeps NULL in both columns (we do not invent a publisher after the fact).
--
-- Run ONCE against a 'smartshift' database that already has migration 008.
-- It is not safe to run twice (the columns would already exist).
-- ============================================================

USE smartshift;

ALTER TABLE schedule
    ADD COLUMN published_by INT NULL AFTER generated_at,
    ADD COLUMN published_at DATETIME NULL AFTER published_by,
    ADD CONSTRAINT fk_schedule_published_by
        FOREIGN KEY (published_by) REFERENCES user_account(user_id)
        ON UPDATE CASCADE ON DELETE SET NULL;
