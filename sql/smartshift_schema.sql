-- ============================================================
-- SmartShift: Constraint-Based Shift Scheduling and Payroll
-- Optimisation for SMEs — Database Schema (MySQL 8.0, InnoDB)
-- ============================================================

CREATE DATABASE IF NOT EXISTS smartshift
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE smartshift;

-- ------------------------------------------------------------
-- 1. USER_ACCOUNT  — manager/admin login
-- ------------------------------------------------------------
CREATE TABLE user_account (
    user_id         INT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(100) NOT NULL,
    role            ENUM('MANAGER','ADMIN') NOT NULL DEFAULT 'MANAGER',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 2. ROLE  — job roles / skill tags (Barista, Cashier, Cook...)
-- ------------------------------------------------------------
CREATE TABLE role (
    role_id         INT AUTO_INCREMENT PRIMARY KEY,
    role_name       VARCHAR(50) NOT NULL UNIQUE,
    description     VARCHAR(255)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 3. EMPLOYEE
-- ------------------------------------------------------------
CREATE TABLE employee (
    employee_id          INT AUTO_INCREMENT PRIMARY KEY,
    role_id              INT NOT NULL,
    full_name            VARCHAR(100) NOT NULL,
    contact_number       VARCHAR(20),
    bank_account_number  VARCHAR(30),
    employment_type      ENUM('FULL_TIME','PART_TIME') NOT NULL DEFAULT 'FULL_TIME',
    hourly_rate          DECIMAL(8,2) NOT NULL CHECK (hourly_rate > 0),
    max_hours_week       DECIMAL(5,2) NOT NULL DEFAULT 45.00,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_employee_role
        FOREIGN KEY (role_id) REFERENCES role(role_id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 4. AVAILABILITY  — recurring weekly availability per employee
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
-- 5. SHIFT_DEFINITION  — the shift "slots" a schedule is built from
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
-- 6. EMPLOYEE_PREFERENCE  — soft-constraint input (preferred shifts)
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
-- 7. CONSTRAINT_RULE  — configurable hard/soft rules & weights
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
-- 8. SCHEDULE  — one generated weekly roster (a "run" of the solver)
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
-- 9. SCHEDULE_ASSIGNMENT  — solver output: employee <-> shift, per schedule
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
-- 10. CONSTRAINT_VIOLATION  — logged whenever a rule is broken/relaxed
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
-- 11. PAYROLL_RECORD  — computed directly from schedule_assignment
-- ------------------------------------------------------------
CREATE TABLE payroll_record (
    payroll_id        INT AUTO_INCREMENT PRIMARY KEY,
    schedule_id       INT NOT NULL,
    employee_id       INT NOT NULL,
    regular_hours     DECIMAL(6,2) NOT NULL DEFAULT 0,
    overtime_hours    DECIMAL(6,2) NOT NULL DEFAULT 0,
    regular_pay       DECIMAL(10,2) NOT NULL DEFAULT 0,
    overtime_pay      DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_pay         DECIMAL(10,2) GENERATED ALWAYS AS (regular_pay + overtime_pay) STORED,
    computed_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payroll_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule(schedule_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_payroll_employee
        FOREIGN KEY (employee_id) REFERENCES employee(employee_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_payroll UNIQUE (schedule_id, employee_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 12. OBJECTIVE_WEIGHT — configurable lambda weights for the weighted
--     objective Z (Section 3.4.4), one row per schedule run so a manager
--     can retune the trade-off before regenerating a roster.
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
-- 13. BASELINE_SCHEDULE — rule-based/manual baseline roster, persisted
--     alongside the CP-SAT result so FYP2's RQ3 comparison
--     (Section 3.10) has a documented baseline to evaluate against.
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
-- 14. COMPLIANCE_REPORT  — one summary row per schedule run
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
-- 15. EVALUATION_METRIC  — runtime/quality metrics for FYP2 testing
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
-- 16. AUDIT_LOG  — traceability: who changed what, when
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
-- 17. PUBLIC_HOLIDAY — gazetted public holidays (supervisor item #4).
--     Checked against each shift's actual calendar date (Shift.actualDate,
--     Java side) to apply the 2x public-holiday pay premium in
--     PayrollCalculator - see Section 3.5.
-- ------------------------------------------------------------
CREATE TABLE public_holiday (
    holiday_id    INT AUTO_INCREMENT PRIMARY KEY,
    holiday_date  DATE NOT NULL UNIQUE,
    description   VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Helpful indexes for solver queries
-- ------------------------------------------------------------
CREATE INDEX idx_availability_employee_day ON availability(employee_id, day_of_week);
CREATE INDEX idx_shift_day ON shift_definition(day_of_week);
CREATE INDEX idx_assignment_schedule ON schedule_assignment(schedule_id);
CREATE INDEX idx_payroll_schedule ON payroll_record(schedule_id);
CREATE INDEX idx_baseline_schedule ON baseline_schedule(schedule_id);
