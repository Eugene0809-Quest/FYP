-- ============================================================
-- Migration 008 (FYP2, phase 2): job positions inside a department.
--
-- 'role' stays the DEPARTMENT / skill group the solver matches on
-- (Front-of-House, Kitchen), so constraint C2 and every shift
-- definition are unchanged. 'job_position' holds the job titles inside
-- a department (Cashier, Supervisor, Head Chef, ...). A senior position
-- can still cover any normal shift of its department.
--
-- The composite foreign key on employee (role_id, job_position_id)
-- guarantees that an employee's position belongs to the SAME
-- department as the employee's role, so a Head Chef cannot be filed
-- under Front-of-House. job_position_id is nullable so the existing
-- employees stay valid until positions are assigned.
--
-- Table is named job_position (not 'position') to avoid clashing with
-- the SQL POSITION() function name.
--
-- Run ONCE against a 'smartshift' database that already has migration
-- 007 applied. This file creates structure only; the position rows
-- themselves are data and are loaded by the story seed script.
-- ============================================================

USE smartshift;

CREATE TABLE job_position (
    job_position_id  INT AUTO_INCREMENT PRIMARY KEY,
    role_id          INT NOT NULL,
    position_name    VARCHAR(50) NOT NULL,
    rank_level       TINYINT NOT NULL DEFAULT 1,
    description      VARCHAR(255),
    CONSTRAINT uq_position_name UNIQUE (role_id, position_name),
    CONSTRAINT uq_position_role_id UNIQUE (role_id, job_position_id),
    CONSTRAINT chk_position_rank CHECK (rank_level BETWEEN 1 AND 5),
    CONSTRAINT fk_position_role
        FOREIGN KEY (role_id) REFERENCES role(role_id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

ALTER TABLE employee
    ADD COLUMN job_position_id INT NULL AFTER role_id;

ALTER TABLE employee
    ADD CONSTRAINT fk_employee_position
        FOREIGN KEY (role_id, job_position_id)
        REFERENCES job_position (role_id, job_position_id);
