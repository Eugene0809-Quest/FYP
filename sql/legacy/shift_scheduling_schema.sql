-- =====================================================================
-- Constraint-Based Shift Scheduling and Payroll Optimisation for SMEs
-- Database Schema (MySQL 8.0)
-- =====================================================================

DROP DATABASE IF EXISTS shift_scheduling;
CREATE DATABASE shift_scheduling CHARACTER SET utf8mb4;
USE shift_scheduling;

-- ---------------------------------------------------------------------
-- 1. ROLE : job roles / skills (e.g. Kitchen, Front-of-House, Cashier)
-- ---------------------------------------------------------------------
CREATE TABLE role (
    role_id      INT PRIMARY KEY AUTO_INCREMENT,
    role_name    VARCHAR(50) NOT NULL UNIQUE
);

-- ---------------------------------------------------------------------
-- 2. EMPLOYEE
-- ---------------------------------------------------------------------
CREATE TABLE employee (
    employee_id     INT PRIMARY KEY AUTO_INCREMENT,
    full_name       VARCHAR(100) NOT NULL,
    role_id         INT NOT NULL,
    hourly_rate     DECIMAL(8,2) NOT NULL CHECK (hourly_rate > 0),
    max_weekly_hours DECIMAL(5,2) NOT NULL DEFAULT 45.00,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (role_id) REFERENCES role(role_id)
);

-- ---------------------------------------------------------------------
-- 3. WEEK_PLAN : one row per weekly roster run
-- ---------------------------------------------------------------------
CREATE TABLE week_plan (
    week_plan_id    INT PRIMARY KEY AUTO_INCREMENT,
    week_start_date DATE NOT NULL,
    status          ENUM('DRAFT','SOLVED','PUBLISHED') NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
-- 4. SHIFT : shift definitions for a given week plan
-- ---------------------------------------------------------------------
CREATE TABLE shift (
    shift_id       INT PRIMARY KEY AUTO_INCREMENT,
    week_plan_id   INT NOT NULL,
    day_of_week    TINYINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time     TIME NOT NULL,
    end_time       TIME NOT NULL,
    required_role_id INT NOT NULL,
    staff_needed   INT NOT NULL DEFAULT 1,
    FOREIGN KEY (week_plan_id) REFERENCES week_plan(week_plan_id) ON DELETE CASCADE,
    FOREIGN KEY (required_role_id) REFERENCES role(role_id)
);

-- ---------------------------------------------------------------------
-- 5. AVAILABILITY : employee-declared availability windows per week plan
-- ---------------------------------------------------------------------
CREATE TABLE availability (
    availability_id INT PRIMARY KEY AUTO_INCREMENT,
    employee_id     INT NOT NULL,
    week_plan_id    INT NOT NULL,
    day_of_week     TINYINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    FOREIGN KEY (employee_id) REFERENCES employee(employee_id) ON DELETE CASCADE,
    FOREIGN KEY (week_plan_id) REFERENCES week_plan(week_plan_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- 6. PREFERENCE : soft shift preferences (for the preference-violation term)
-- ---------------------------------------------------------------------
CREATE TABLE preference (
    preference_id   INT PRIMARY KEY AUTO_INCREMENT,
    employee_id     INT NOT NULL,
    shift_id        INT NOT NULL,
    preference_level ENUM('PREFERRED','NEUTRAL','AVOID') NOT NULL DEFAULT 'NEUTRAL',
    FOREIGN KEY (employee_id) REFERENCES employee(employee_id) ON DELETE CASCADE,
    FOREIGN KEY (shift_id) REFERENCES shift(shift_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- 7. ASSIGNMENT : the solver's output -> x[e,s] = 1 entries
-- ---------------------------------------------------------------------
CREATE TABLE assignment (
    assignment_id   INT PRIMARY KEY AUTO_INCREMENT,
    week_plan_id    INT NOT NULL,
    employee_id     INT NOT NULL,
    shift_id        INT NOT NULL,
    FOREIGN KEY (week_plan_id) REFERENCES week_plan(week_plan_id) ON DELETE CASCADE,
    FOREIGN KEY (employee_id) REFERENCES employee(employee_id),
    FOREIGN KEY (shift_id) REFERENCES shift(shift_id),
    UNIQUE KEY uq_emp_shift (week_plan_id, employee_id, shift_id)
);

-- ---------------------------------------------------------------------
-- 8. PAYROLL_RECORD : one row per employee per week plan (Section 3.5)
-- ---------------------------------------------------------------------
CREATE TABLE payroll_record (
    payroll_id      INT PRIMARY KEY AUTO_INCREMENT,
    week_plan_id    INT NOT NULL,
    employee_id     INT NOT NULL,
    total_hours     DECIMAL(6,2) NOT NULL,
    regular_hours   DECIMAL(6,2) NOT NULL,
    overtime_hours  DECIMAL(6,2) NOT NULL,
    regular_pay     DECIMAL(10,2) NOT NULL,
    overtime_pay    DECIMAL(10,2) NOT NULL,
    total_pay       DECIMAL(10,2) NOT NULL,
    generated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (week_plan_id) REFERENCES week_plan(week_plan_id) ON DELETE CASCADE,
    FOREIGN KEY (employee_id) REFERENCES employee(employee_id),
    UNIQUE KEY uq_week_emp (week_plan_id, employee_id)
);

-- ---------------------------------------------------------------------
-- 9. CONSTRAINT_VIOLATION : compliance report entries (Section 3.10)
-- ---------------------------------------------------------------------
CREATE TABLE constraint_violation (
    violation_id    INT PRIMARY KEY AUTO_INCREMENT,
    week_plan_id    INT NOT NULL,
    employee_id     INT NULL,
    shift_id        INT NULL,
    violation_type  ENUM('AVAILABILITY','COVERAGE','OVERLAP','MAX_HOURS','ROLE_MISMATCH','UNFILLED_DEMAND') NOT NULL,
    details         VARCHAR(255),
    FOREIGN KEY (week_plan_id) REFERENCES week_plan(week_plan_id) ON DELETE CASCADE,
    FOREIGN KEY (employee_id) REFERENCES employee(employee_id),
    FOREIGN KEY (shift_id) REFERENCES shift(shift_id)
);

-- ---------------------------------------------------------------------
-- 10. OBJECTIVE_WEIGHT : configurable lambda weights (Section 3.4.4)
-- ---------------------------------------------------------------------
CREATE TABLE objective_weight (
    week_plan_id    INT PRIMARY KEY,
    lambda_overtime      DECIMAL(6,2) NOT NULL DEFAULT 1.00,
    lambda_workload      DECIMAL(6,2) NOT NULL DEFAULT 1.00,
    lambda_preference    DECIMAL(6,2) NOT NULL DEFAULT 1.00,
    lambda_unfilled      DECIMAL(6,2) NOT NULL DEFAULT 100.00,
    FOREIGN KEY (week_plan_id) REFERENCES week_plan(week_plan_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- 11. EVALUATION_RUN : FYP2 evaluation metadata (Section 3.10)
-- ---------------------------------------------------------------------
CREATE TABLE evaluation_run (
    eval_run_id     INT PRIMARY KEY AUTO_INCREMENT,
    week_plan_id    INT NOT NULL,
    employee_count  INT NOT NULL,
    runtime_ms      BIGINT,
    memory_kb       BIGINT,
    feasible        BOOLEAN,
    objective_value DECIMAL(12,2),
    FOREIGN KEY (week_plan_id) REFERENCES week_plan(week_plan_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- 12. BASELINE_SCHEDULE : rule-based baseline for comparison (Section 3.10)
-- ---------------------------------------------------------------------
CREATE TABLE baseline_schedule (
    baseline_id     INT PRIMARY KEY AUTO_INCREMENT,
    week_plan_id    INT NOT NULL,
    employee_id     INT NOT NULL,
    shift_id        INT NOT NULL,
    FOREIGN KEY (week_plan_id) REFERENCES week_plan(week_plan_id) ON DELETE CASCADE,
    FOREIGN KEY (employee_id) REFERENCES employee(employee_id),
    FOREIGN KEY (shift_id) REFERENCES shift(shift_id)
);

-- ---------------------------------------------------------------------
-- 13. USER_ACCOUNT : manager login (single-outlet, single manager)
-- ---------------------------------------------------------------------
CREATE TABLE user_account (
    user_id         INT PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(50) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
-- 14. AUDIT_LOG : traceability for roster/payroll regeneration
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    audit_id        INT PRIMARY KEY AUTO_INCREMENT,
    week_plan_id    INT NOT NULL,
    action          VARCHAR(100) NOT NULL,
    action_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (week_plan_id) REFERENCES week_plan(week_plan_id) ON DELETE CASCADE
);
