-- ============================================================
-- SmartShift: Constraint-Based Shift Scheduling and Payroll
-- Optimisation for SMEs - Database Schema (MySQL 8.0, InnoDB)
--
-- CONSOLIDATED SCHEMA (20 tables). For a NEW database this single
-- file is enough: it already includes what migrations 002, 004, 005,
-- 007 and 008 added (employee registration fields, public_holiday
-- table, extended payroll_record, company profile, employee statutory
-- numbers, staff login link, leave requests, job positions). Seed data
-- lives in seed_smartshift_n5.sql.
-- The files in sql/migrations/ are kept only as history for databases
-- that were built before this consolidation.
--
-- Run on an EMPTY server/database only: it uses CREATE TABLE without
-- DROP, so it will error on a database that already has these tables.
-- ============================================================

CREATE DATABASE IF NOT EXISTS smartshift
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE smartshift;

-- ------------------------------------------------------------
-- 1. USER_ACCOUNT  - manager/admin login
-- ------------------------------------------------------------
CREATE TABLE user_account (
    user_id         INT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(100) NOT NULL,
    role            ENUM('MANAGER','ADMIN','STAFF') NOT NULL DEFAULT 'MANAGER',
    employee_id     INT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_employee UNIQUE (employee_id)
    -- fk_user_employee and chk_staff_has_employee are added after the
    -- employee table exists (see "Cross-table rules" near the end).
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 2. ROLE  - job roles / skill tags (Kitchen, Front-of-House...)
-- ------------------------------------------------------------
CREATE TABLE role (
    role_id         INT AUTO_INCREMENT PRIMARY KEY,
    role_name       VARCHAR(50) NOT NULL UNIQUE,
    description     VARCHAR(255)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 20. JOB_POSITION  (migration 008) - job titles inside a department
--     (role). Placed here, before employee, because employee has a
--     composite foreign key to it. 'role' stays the department the
--     solver matches on; a senior position can cover any normal shift
--     of its own department.
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 3. EMPLOYEE  (includes migration 002 fields)
-- ------------------------------------------------------------
CREATE TABLE employee (
    employee_id          INT AUTO_INCREMENT PRIMARY KEY,
    role_id              INT NOT NULL,
    job_position_id      INT NULL,
    full_name            VARCHAR(100) NOT NULL,
    ic_number            VARCHAR(20) NULL,
    contact_number       VARCHAR(20),
    bank_account_number  VARCHAR(30),
    epf_number           VARCHAR(20) NULL,
    socso_number         VARCHAR(20) NULL,
    eis_number           VARCHAR(20) NULL,
    employment_type      ENUM('FULL_TIME','PART_TIME') NOT NULL DEFAULT 'FULL_TIME',
    date_joined          DATE NULL,
    hourly_rate          DECIMAL(8,2) NOT NULL CHECK (hourly_rate > 0),
    max_hours_week       DECIMAL(5,2) NOT NULL DEFAULT 45.00,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_employee_ic UNIQUE (ic_number),
    CONSTRAINT fk_employee_role
        FOREIGN KEY (role_id) REFERENCES role(role_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    -- A position must belong to the SAME department as the employee's
    -- role. job_position_id may be empty (no position assigned yet).
    CONSTRAINT fk_employee_position
        FOREIGN KEY (role_id, job_position_id)
        REFERENCES job_position (role_id, job_position_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 4. AVAILABILITY  - recurring weekly availability per employee
-- ------------------------------------------------------------
CREATE TABLE availability (
    availability_id INT AUTO_INCREMENT PRIMARY KEY,
    employee_id     INT NOT NULL,
    day_of_week     ENUM('MON','TUE','WED','THU','FRI','SAT','SUN') NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    CONSTRAINT fk_availability_employee
        FOREIGN KEY (employee_id) REFERENCES employee(employee_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_availability_time CHECK (end_time > start_time)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 5. SHIFT_DEFINITION  - the shift "slots" a schedule is built from
-- ------------------------------------------------------------
CREATE TABLE shift_definition (
    shift_id        INT AUTO_INCREMENT PRIMARY KEY,
    role_id         INT NOT NULL,
    day_of_week     ENUM('MON','TUE','WED','THU','FRI','SAT','SUN') NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    required_staff  INT NOT NULL DEFAULT 1 CHECK (required_staff > 0),
    CONSTRAINT fk_shift_role
        FOREIGN KEY (role_id) REFERENCES role(role_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_shift_time CHECK (end_time > start_time)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 6. EMPLOYEE_PREFERENCE  - soft-constraint input (preferred shifts)
-- ------------------------------------------------------------
CREATE TABLE employee_preference (
    preference_id     INT AUTO_INCREMENT PRIMARY KEY,
    employee_id       INT NOT NULL,
    shift_id          INT NOT NULL,
    preference_level  TINYINT NOT NULL DEFAULT 0 CHECK (preference_level BETWEEN -2 AND 2),
    CONSTRAINT fk_preference_employee
        FOREIGN KEY (employee_id) REFERENCES employee(employee_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_preference_shift
        FOREIGN KEY (shift_id) REFERENCES shift_definition(shift_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT uq_preference UNIQUE (employee_id, shift_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 7. CONSTRAINT_RULE  - configurable hard/soft rules & weights
-- ------------------------------------------------------------
CREATE TABLE constraint_rule (
    rule_id         INT AUTO_INCREMENT PRIMARY KEY,
    rule_code       VARCHAR(50) NOT NULL UNIQUE,
    rule_name       VARCHAR(100) NOT NULL,
    rule_type       ENUM('HARD','SOFT') NOT NULL,
    weight          DECIMAL(6,2) NOT NULL DEFAULT 1.00,
    is_enabled      BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 8. SCHEDULE  - one generated weekly roster (a "run" of the solver)
-- ------------------------------------------------------------
CREATE TABLE schedule (
    schedule_id       INT AUTO_INCREMENT PRIMARY KEY,
    week_start_date   DATE NOT NULL,
    status            ENUM('DRAFT','GENERATED','APPROVED') NOT NULL DEFAULT 'DRAFT',
    objective_value   DECIMAL(12,2),
    generated_by      INT,
    generated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_schedule_user
        FOREIGN KEY (generated_by) REFERENCES user_account(user_id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 9. SCHEDULE_ASSIGNMENT  - solver output: employee <-> shift
-- ------------------------------------------------------------
CREATE TABLE schedule_assignment (
    assignment_id     INT AUTO_INCREMENT PRIMARY KEY,
    schedule_id       INT NOT NULL,
    shift_id          INT NOT NULL,
    employee_id       INT NOT NULL,
    assigned_hours    DECIMAL(5,2) NOT NULL,
    CONSTRAINT fk_assignment_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule(schedule_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_assignment_shift
        FOREIGN KEY (shift_id) REFERENCES shift_definition(shift_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_employee
        FOREIGN KEY (employee_id) REFERENCES employee(employee_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_assignment UNIQUE (schedule_id, shift_id, employee_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 10. CONSTRAINT_VIOLATION  - logged whenever a rule is broken/relaxed
-- ------------------------------------------------------------
CREATE TABLE constraint_violation (
    violation_id      INT AUTO_INCREMENT PRIMARY KEY,
    schedule_id       INT NOT NULL,
    rule_id           INT NOT NULL,
    employee_id       INT,
    shift_id          INT,
    severity          DECIMAL(6,2) NOT NULL DEFAULT 0,
    description       VARCHAR(255),
    CONSTRAINT fk_violation_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule(schedule_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_violation_rule
        FOREIGN KEY (rule_id) REFERENCES constraint_rule(rule_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_violation_employee
        FOREIGN KEY (employee_id) REFERENCES employee(employee_id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_violation_shift
        FOREIGN KEY (shift_id) REFERENCES shift_definition(shift_id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 11. PAYROLL_RECORD  - computed from schedule_assignment
--     (includes migration 005: holiday pay, EPF/SOCSO/EIS, net pay,
--      employer total cost; total_pay now includes holiday_pay)
-- ------------------------------------------------------------
CREATE TABLE payroll_record (
    payroll_id            INT AUTO_INCREMENT PRIMARY KEY,
    schedule_id           INT NOT NULL,
    employee_id           INT NOT NULL,
    regular_hours         DECIMAL(6,2)  NOT NULL DEFAULT 0,
    overtime_hours        DECIMAL(6,2)  NOT NULL DEFAULT 0,
    holiday_hours         DECIMAL(6,2)  NOT NULL DEFAULT 0,
    regular_pay           DECIMAL(10,2) NOT NULL DEFAULT 0,
    overtime_pay          DECIMAL(10,2) NOT NULL DEFAULT 0,
    holiday_pay           DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_pay             DECIMAL(10,2)
        GENERATED ALWAYS AS (regular_pay + overtime_pay + holiday_pay) STORED,
    employee_epf          DECIMAL(10,2) NOT NULL DEFAULT 0,
    employer_epf          DECIMAL(10,2) NOT NULL DEFAULT 0,
    employee_socso        DECIMAL(10,2) NOT NULL DEFAULT 0,
    employer_socso        DECIMAL(10,2) NOT NULL DEFAULT 0,
    employee_eis          DECIMAL(10,2) NOT NULL DEFAULT 0,
    employer_eis          DECIMAL(10,2) NOT NULL DEFAULT 0,
    net_pay               DECIMAL(10,2) NOT NULL DEFAULT 0,
    employer_total_cost   DECIMAL(10,2) NOT NULL DEFAULT 0,
    computed_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payroll_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule(schedule_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_payroll_employee
        FOREIGN KEY (employee_id) REFERENCES employee(employee_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_payroll UNIQUE (schedule_id, employee_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 12. OBJECTIVE_WEIGHT - configurable lambda weights for the weighted
--     objective Z (Section 3.4.4), one row per schedule run.
-- ------------------------------------------------------------
CREATE TABLE objective_weight (
    schedule_id       INT PRIMARY KEY,
    lambda_overtime   DECIMAL(6,2) NOT NULL DEFAULT 1.00,
    lambda_workload   DECIMAL(6,2) NOT NULL DEFAULT 1.00,
    lambda_preference DECIMAL(6,2) NOT NULL DEFAULT 1.00,
    lambda_unfilled   DECIMAL(6,2) NOT NULL DEFAULT 100.00,
    CONSTRAINT fk_objective_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule(schedule_id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 13. BASELINE_SCHEDULE - rule-based/manual baseline roster, stored
--     beside the CP-SAT result for the FYP2 RQ3 comparison.
-- ------------------------------------------------------------
CREATE TABLE baseline_schedule (
    baseline_id     INT AUTO_INCREMENT PRIMARY KEY,
    schedule_id     INT NOT NULL,
    shift_id        INT NOT NULL,
    employee_id     INT NOT NULL,
    CONSTRAINT fk_baseline_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule(schedule_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_baseline_shift
        FOREIGN KEY (shift_id) REFERENCES shift_definition(shift_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_baseline_employee
        FOREIGN KEY (employee_id) REFERENCES employee(employee_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_baseline_slot UNIQUE (schedule_id, shift_id, employee_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 14. COMPLIANCE_REPORT  - one summary row per schedule run
-- ------------------------------------------------------------
CREATE TABLE compliance_report (
    report_id           INT AUTO_INCREMENT PRIMARY KEY,
    schedule_id         INT NOT NULL UNIQUE,
    total_violations    INT NOT NULL DEFAULT 0,
    hard_violations     INT NOT NULL DEFAULT 0,
    soft_penalty_score  DECIMAL(12,2) NOT NULL DEFAULT 0,
    coverage_percent    DECIMAL(5,2) NOT NULL DEFAULT 0,
    generated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_compliance_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule(schedule_id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 15. EVALUATION_METRIC  - runtime/quality metrics for FYP2 testing
-- ------------------------------------------------------------
CREATE TABLE evaluation_metric (
    metric_id         INT AUTO_INCREMENT PRIMARY KEY,
    schedule_id       INT NOT NULL,
    metric_name       VARCHAR(50) NOT NULL,
    metric_value      DECIMAL(14,4) NOT NULL,
    recorded_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_metric_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule(schedule_id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 16. AUDIT_LOG  - traceability: who changed what, when
-- ------------------------------------------------------------
CREATE TABLE audit_log (
    log_id            INT AUTO_INCREMENT PRIMARY KEY,
    user_id           INT,
    action            VARCHAR(100) NOT NULL,
    table_affected    VARCHAR(50),
    record_id         INT,
    logged_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user
        FOREIGN KEY (user_id) REFERENCES user_account(user_id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 17. PUBLIC_HOLIDAY  (migration 004) - dates paid at the holiday
--     rate by the payroll calculator. No foreign keys.
-- ------------------------------------------------------------
CREATE TABLE public_holiday (
    holiday_id      INT AUTO_INCREMENT PRIMARY KEY,
    holiday_date    DATE NOT NULL UNIQUE,
    description     VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 18. COMPANY  (migration 007) - exactly one row (single-outlet scope).
--     The CHECK forces company_id = 1, so a second company cannot be
--     inserted by mistake.
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
-- 19. LEAVE_REQUEST  (migration 007) - staff ask for dated time off;
--     an admin approves or rejects. Approved leave will be read by the
--     solver as "unavailable on these dates".
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

-- ------------------------------------------------------------
-- Cross-table rules: user_account is created before employee, so the
-- link from a login to an employee is added here. MySQL forbids CHECK
-- constraints on columns that have ON DELETE / ON UPDATE actions, so
-- this foreign key uses the default RESTRICT behaviour (employees are
-- deactivated, not deleted).
-- ------------------------------------------------------------
ALTER TABLE user_account
    ADD CONSTRAINT fk_user_employee
        FOREIGN KEY (employee_id) REFERENCES employee(employee_id),
    ADD CONSTRAINT chk_staff_has_employee
        CHECK (role <> 'STAFF' OR employee_id IS NOT NULL);

-- ------------------------------------------------------------
-- Helpful indexes for solver queries
-- ------------------------------------------------------------
CREATE INDEX idx_availability_employee_day ON availability(employee_id, day_of_week);
CREATE INDEX idx_shift_day ON shift_definition(day_of_week);
CREATE INDEX idx_assignment_schedule ON schedule_assignment(schedule_id);
CREATE INDEX idx_payroll_schedule ON payroll_record(schedule_id);
CREATE INDEX idx_baseline_schedule ON baseline_schedule(schedule_id);
CREATE INDEX idx_leave_employee_status ON leave_request(employee_id, status);
CREATE INDEX idx_leave_dates ON leave_request(start_date, end_date);
