# SmartShift - Constraint-Based Shift Scheduling and Payroll Optimisation for SMEs

FYP1 prototype (BCS 3113 Project 1). A JavaFX desktop app that builds a weekly staff roster for a
single-outlet F&B business with the OR-Tools CP-SAT solver (or by hand), then calculates estimated
payroll directly from that roster - including public-holiday pay and EPF/SOCSO/EIS - from the same
MySQL data.

> **Estimated payroll only.** This is a study prototype, not a certified payslip. PCB income tax is
> not implemented, and the statutory contributions use flat percentages rather than the official
> wage-band tables (see "Known simplifications").

## What it does

| Area | Feature |
|---|---|
| Access | Login screen (`user_account`, SHA-256 hashed passwords) |
| Employees | Register employees (name, position/role, bank account, full/part-time with default 45h/30h weekly cap, hourly rate); per-employee weekly availability editor |
| Roster - Automatic | CP-SAT solver assigns employees to shifts under hard constraints (availability, role match, coverage, no overlap, weekly hour cap) and a weighted objective |
| Roster - Manual | Admin picks who works each shift slot, in one sub-tab per role. Dropdowns are pre-filtered to role-matched, available employees; overlap and weekly-hour-cap problems are shown as warnings (not blocked) |
| Payroll | Regular / overtime / public-holiday pay, EPF, SOCSO, EIS, net pay and employer cost, calculated from the roster by the same `PayrollCalculator` in both modes |
| Public holidays | "Public Holidays" tab to maintain the holiday calendar; a "Week starting" picker maps the weekly shift template onto real dates so holiday shifts are paid at 2x |

## What has actually been tested

Last updated after step 1 of the post-supervisor-feedback plan (unit tests). "Verified" below means it
was run on the author's Windows machine (MySQL 8.0, Maven) and the output checked by hand.

| Component | Status | How it was verified |
|---|---|---|
| MySQL schema + seed (`sql/`) | **Verified** | Loaded into a live MySQL 8.0 server; the app reads and writes it. `sql/migrations/004_add_public_holiday_table.sql` also run. |
| Java build | **Verified** | `mvn clean compile` - 31 main source files, BUILD SUCCESS |
| Unit tests | **Verified** | `mvn test` - 10 tests, 0 failures: `PayrollCalculatorTest` (6: holiday 2x pay, original Table 3.1 case, EPF employer 13%/12% tiers, SOCSO/EIS RM6,000 ceiling) and `ManualScheduleValidatorTest` (4: clean schedule, overlap, weekly-hour cap, coverage). Expected figures were hand-computed with exact decimal arithmetic first. |
| `PayrollCalculatorManualTest` | **Verified earlier** | Plain `main()` test matching report Table 3.1 (RM594.00 for 48h at RM12/h) |
| `SchedulingEngine` (CP-SAT) | **Verified by running, not by unit test** | Automatic mode returns OPTIMAL with 0 unfilled slots on the seeded data (later plus employees registered through the UI). No automated test asserts solver output. |
| JavaFX UI | **Verified by hand only** | Login, employee registration, availability editor, Automatic and Manual roster modes, holiday-aware payroll and the EPF/SOCSO/EIS columns were exercised manually. The add/remove flow on the Public Holidays tab has not been separately confirmed. There are no automated UI tests. |
| DAO layer | **Needs a re-run** | `DaoIntegrationTest` needs a live database and is run by hand (see below). Its assertions were relaxed (employees/availability must be non-empty; shifts must be exactly 28) because employee data now changes through the app - re-run it to confirm it passes. |

## Running it

**Requirements:** JDK 21 or newer (the pom targets Java 21; the author's machine runs Temurin 25),
Maven, MySQL 8.0.

### 1. Database
```powershell
# PowerShell has no "<" redirection, so pipe the files in:
Get-Content sql/smartshift_schema.sql        | mysql -u root -p
Get-Content sql/seed_smartshift_n5.sql       | mysql -u root -p
Get-Content sql/migrations/004_add_public_holiday_table.sql | mysql -u root -p
```
(In cmd or bash, `mysql -u root -p < file.sql` works as usual.) The schema file already includes the
`public_holiday` table; migration 004 is what inserts the 2026 holiday rows and is safe to run on a
fresh or existing database. Migrations 002/003 are only for databases created before those changes.

Default demo login: `admin` / `admin123`. It is a known demo credential - change it before any real use.

### 2. Database password
`DatabaseConnection.java` reads the password from the `SMARTSHIFT_DB_PASSWORD` environment variable
(it is not stored in the repo). To set it permanently for your Windows account:
```powershell
[System.Environment]::SetEnvironmentVariable("SMARTSHIFT_DB_PASSWORD", "your_password", "User")
```
Then close and reopen your terminal / VS Code - running processes do not see the change. Leave it
unset if your local root password is blank.

### 3. Build and run
```powershell
mvn clean compile
mvn exec:java "-Dexec.mainClass=com.questiu.scheduler.ui.MainApp"      # JavaFX UI
mvn exec:java "-Dexec.mainClass=com.questiu.scheduler.Main"            # console pipeline: DB -> solver -> payroll -> print
```
`exec:java` does not recompile, so run `mvn clean compile` (or `mvn compile exec:java`) after changing code.

**Use `exec:java`, not `mvn javafx:run`.** On the author's machine `javafx:run` builds a Java module path
and drops the OR-Tools native-library jars (their automatic module names are invalid), which crashes the
solver with "Resource ortools-win32-x86-64/ was not found". `exec:java` uses a plain classpath and does not
have this problem. The "Invalid module name: '64'" warnings printed by Maven are harmless.

### 4. Tests
```powershell
mvn test    # unit tests, no database needed
mvn exec:java "-Dexec.mainClass=com.questiu.scheduler.DaoIntegrationTest" "-Dexec.classpathScope=test"    # needs the database
```

## How payroll is calculated

Per employee, from the assigned roster (`PayrollCalculator`):

- Hours on a shift whose calendar date is a public holiday are **holiday hours**; all others are normal hours.
- `RegularPay = rate x min(normalHours, maxHoursWeek)`
- `OvertimePay = rate x 1.5 x max(0, normalHours - maxHoursWeek)`
- `HolidayPay = rate x 2.0 x holidayHours`
- `Gross = RegularPay + OvertimePay + HolidayPay`
- Statutory wage base = `RegularPay + HolidayPay` (overtime is excluded, following KWSP's "payments not subject to EPF" guidance)
- **EPF:** employee 11%; employer 13% if the estimated monthly wage is <= RM5,000, otherwise 12%
- **SOCSO** (Category 1, under 60): employee 0.5%, employer 1.75%, on wages up to the RM6,000/month ceiling
- **EIS:** employee 0.2%, employer 0.2%, same ceiling
- `Net = Gross - employee EPF - employee SOCSO - employee EIS`; `Employer cost = Gross + employer EPF + employer SOCSO + employer EIS`

Rates and thresholds live in `StatutoryRates.java` and were checked against several published 2026 sources
when they were added; they should be re-verified against KWSP / PERKESO before being relied on.

## Solver objective (report Section 3.4.4)

`Z = LabourCost + lambda1*OvertimeCost + lambda2*WorkloadImbalance + lambda3*PreferenceViolations + lambda4*UnfilledDemand`

| Term | Status in `SchedulingEngine.java` |
|---|---|
| UnfilledDemand | Implemented, dominant weight |
| LabourCost | Implemented as a proxy (proportional to hours assigned) |
| WorkloadImbalance | Implemented: minimises the spread between the most- and least-loaded employee **within each role group** |
| OvertimeCost | Present but a deliberate, documented no-op. Hard constraint 5 already forbids exceeding `max_hours_week`, so real overtime cannot occur in a solver-generated roster; matching the soft threshold to the cap would leave the term equally inert. |
| PreferenceViolations | **Not implemented** - the `employee_preference` table exists but the solver never reads it |

## Known simplifications and limitations

**Payroll and statutory**
- Public-holiday hours are paid at a flat 2x and kept out of the regular/overtime pool. The Employment Act's
  differing treatment of monthly-, daily- and hourly-rated staff is not modelled.
- EPF/SOCSO/EIS use flat percentages, not KWSP's Third Schedule or PERKESO's wage-band tables, so a real payslip
  can differ by a few sen.
- The system produces one week at a time with no stored history, so a monthly wage is estimated as
  `weekly wage x 52/12` only to pick the EPF employer tier and to apply the SOCSO/EIS ceiling; the RM amounts
  charged are for that week's actual wage.
- Assumes Malaysian citizens under 60. Senior-citizen and foreign-worker rates are not modelled. PCB is out of scope.
- One national holiday list (2026 seed data): no per-state variation, and Islamic-calendar dates can still shift
  pending official confirmation.

**Solver**
- Objective weights are hard-coded constants; the `objective_weight` table's lambda columns are not read.
  The scale of the individual terms is ad hoc and has not been tuned or justified yet.
- Availability is a recurring weekly pattern and shifts are a weekly template; the "Week starting" date is used
  only to match holidays. There is no leave management.
- **Performance:** after the per-role workload term was added, one observed solve on 7 employees took about 9.5 s
  against the 10 s limit (still OPTIMAL), versus tens of milliseconds before. This is a single observation, but it
  needs profiling before the FYP2 tests on up to 30 employees, where the solver may return FEASIBLE instead of OPTIMAL.

**Application**
- Nothing is written back to `schedule`, `schedule_assignment`, `payroll_record`, `compliance_report` or the other
  result tables yet - results are only displayed. Only `role`, `employee`, `availability`, `shift_definition`,
  `user_account` and `public_holiday` are actively used.
- Manual mode reports overlap and weekly-cap problems as warnings rather than blocking them.
- Passwords are hashed with unsalted SHA-256, which is fine for a demo and not for production (use bcrypt or Argon2).
- `python_validation/solver_prototype.py` still targets the old `shift_scheduling` schema and has not been ported.

## Next steps

1. Implement the `PreferenceViolations` term (read `employee_preference` in the solver).
2. Persist results to the schedule / assignment / payroll / compliance tables.
3. Profile solver time as employee count grows and tune the objective weights.
4. Bring the written report and slides in line with what is built (notably: EPF/SOCSO/EIS are now in scope).

## File map

```
sme-scheduler/
  pom.xml                                   Maven build (OR-Tools, MySQL connector, JavaFX, JUnit 5)
  sql/
    smartshift_schema.sql                   17-table DDL (current)
    seed_smartshift_n5.sql                  n=5 synthetic dataset + demo admin login
    migrations/                             002 employee fields, 003 default admin, 004 public_holiday table + 2026 holidays
    legacy/                                 superseded shift_scheduling schema, kept for reference only
  python_validation/solver_prototype.py     early Python CP-SAT prototype (stale - old schema)
  src/main/java/com/questiu/scheduler/
    Main.java                               console pipeline runner
    model/    Employee, EmploymentType, Role, Shift, Availability, PublicHoliday, RosterAssignment,
              PayrollRecord (with StatutoryBreakdown), User, UserRole
    dao/      DatabaseConnection, EmployeeDao, RoleDao, ShiftDao, AvailabilityDao, PublicHolidayDao,
              UserDao, DayOfWeekMapper
    solver/   SchedulingEngine (CP-SAT model), ManualScheduleValidator
    payroll/  PayrollCalculator, StatutoryRates
    ui/       Launcher, MainApp, LoginView, EmployeeRegistrationDialog, AvailabilityEditDialog,
              ManualAssignmentPane, PublicHolidayPane
    util/     PasswordHasher
  src/test/java/com/questiu/scheduler/
    payroll/PayrollCalculatorTest.java              JUnit 5 - 6 tests
    solver/ManualScheduleValidatorTest.java         JUnit 5 - 4 tests
    PayrollCalculatorManualTest.java                plain main() check against report Table 3.1
    DaoIntegrationTest.java                         plain main(), needs a live database
```
