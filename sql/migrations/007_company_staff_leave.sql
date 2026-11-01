-- ============================================================
-- Migration 007 (FYP2, phase 2): company profile, employee
-- statutory numbers, staff login link, leave requests.
--
-- Responds to the lecturer's prototype feedback: a company story and
-- members, EPF/EIS numbers, staff login with admin approval of leave,
-- and a login that leads to either the staff or the admin view.
--
-- Run ONCE against an existing 'smartshift' database that already has
-- the 17-table consolidated schema. It is not safe to run twice.
-- All employee numbers stored by this system must be INVENTED test
-- data, never real IC / EPF / SOCSO / EIS numbers.
-- ============================================================

USE smartshift;

-- ------------------------------------------------------------
-- 1. COMPANY: exactly one row (single-outlet scope).
--    The CHECK forces company_id = 1, so a second company cannot
--    be inserted by mistake.
-- ------------------------------------------------------------
CREATE TABLE company (
    company_id        TINYINT UNSIGNED NOT NULL DEFAULT 1 PRIMARY KEY,
    company_name      VARCHAR(120) NOT NULL,
    registration_no   VARCHAR(30),
    business_type     VARCHAR(60),
    address           VARCHAR(255),
    contact_number    VARCHAR(20),
    email             VARCHAR(100),
    opening_time      TIME,
    closing_time      TIME,
    description       VARCHAR(500),
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_company_single_row CHECK (company_id = 1)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 2. EMPLOYEE: member profile and statutory numbers.
--    Nullable so the existing rows stay valid; the registration
--    screen will make them required for new employees.
-- ------------------------------------------------------------
ALTER TABLE employee
    ADD COLUMN ic_number     VARCHAR(20) NULL AFTER full_name,
    ADD COLUMN epf_number    VARCHAR(20) NULL AFTER bank_account_number,
    ADD COLUMN socso_number  VARCHAR(20) NULL AFTER epf_number,
    ADD COLUMN eis_number    VARCHAR(20) NULL AFTER socso_number,
    ADD COLUMN date_joined   DATE NULL AFTER employment_type,
    ADD CONSTRAINT uq_employee_ic UNIQUE (ic_number);

-- ------------------------------------------------------------
-- 3. USER_ACCOUNT: add the STAFF role and link a login to an
--    employee. A STAFF account must point at an employee; ADMIN and
--    MANAGER accounts do not need to.
--    (MySQL forbids CHECK constraints on columns that have ON DELETE /
--    ON UPDATE actions, so the foreign key below uses the default
--    RESTRICT behaviour. Employees are deactivated, not deleted.)
-- ------------------------------------------------------------
ALTER TABLE user_account
    MODIFY COLUMN role ENUM('MANAGER','ADMIN','STAFF') NOT NULL DEFAULT 'MANAGER';

ALTER TABLE user_account
    ADD COLUMN employee_id INT NULL AFTER role;

ALTER TABLE user_account
    ADD CONSTRAINT uq_user_employee UNIQUE (employee_id),
    ADD CONSTRAINT fk_user_employee
        FOREIGN KEY (employee_id) REFERENCES employee(employee_id),
    ADD CONSTRAINT chk_staff_has_employee
        CHECK (role <> 'STAFF' OR employee_id IS NOT NULL);

-- ------------------------------------------------------------
-- 4. LEAVE_REQUEST: a staff member asks for dated time off; an
--    admin approves or rejects it. Approved leave will later be
--    read by the solver as "unavailable on these dates".
-- ------------------------------------------------------------
CREATE TABLE leave_request (
    leave_id        INT AUTO_INCREMENT PRIMARY KEY,
    employee_id     INT NOT NULL,
    leave_type      ENUM('ANNUAL','SICK','EMERGENCY','UNPAID','OTHER') NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    reason          VARCHAR(255),
    status          ENUM('PENDING','APPROVED','REJECTED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    requested_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_by      INT NULL,
    decided_at      DATETIME NULL,
    admin_note      VARCHAR(255),
    CONSTRAINT fk_leave_employee
        FOREIGN KEY (employee_id) REFERENCES employee(employee_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_leave_decider
        FOREIGN KEY (decided_by) REFERENCES user_account(user_id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_leave_dates CHECK (end_date >= start_date)
) ENGINE=InnoDB;

CREATE INDEX idx_leave_employee_status ON leave_request(employee_id, status);
CREATE INDEX idx_leave_dates ON leave_request(start_date, end_date);
