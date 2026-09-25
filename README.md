# SME Scheduler - FYP1 Prototype

Constraint-Based Shift Scheduling and Payroll Optimisation for SMEs (BCS 3113 Project 1)

## What's in this build, and what's actually been tested

I built and tested this in a sandboxed environment that has Java, MySQL and Python,
but **cannot reach Maven Central** (only a fixed allow-list of domains). That means
two things had to be handled differently:

| Component | Status | How it was verified |
|---|---|---|
| MySQL schema (`sql/schema.sql`) | **Tested for real** | Ran against a live MySQL/MariaDB server. All 14 tables created successfully, n=5 seed data loaded (28 shifts, 33 availability rows). |
| CP-SAT constraint model logic | **Tested for real** | Prototyped in Python (OR-Tools, pip-installable) against the *same* MySQL data. Solved to OPTIMAL with **zero unfilled shifts** for the n=5 scenario. See `python_validation/solver_prototype.py`. |
| `PayrollCalculator.java` | **Tested for real** | Compiled and run with plain `javac`/`java` (no external deps needed). Output matches your report's Table 3.1 example exactly (RM594.00 for 48h at RM12/h). See `src/test/java/.../PayrollCalculatorManualTest.java`. |
| `EmployeeDao`, `ShiftDao`, `AvailabilityDao` | **Tested for real** | Compiled and run against the live MySQL database using the MariaDB JDBC driver. Correctly loaded all 5 employees, 28 shifts, 33 availability rows. See `src/test/java/.../DaoIntegrationTest.java`. |
| `SchedulingEngine.java` (Java/OR-Tools) | **Written, not yet compiled here** | Direct, faithful port of the validated Python logic into the OR-Tools Java API. Needs `ortools-java` from Maven Central, which this sandbox can't reach. **Compile this yourself first** (see below) - it should work, but you must verify it. |
| `MainApp.java` (JavaFX) | **Written, not yet compiled here** | Same reason - JavaFX isn't installed in this sandbox. **Compile and run this yourself first** before presenting it as working. |

**Bottom line: don't present the OR-Tools/JavaFX parts as "tested" until you've actually run
them on your own machine.** Everything else in the table above genuinely ran and produced
the output shown.

## How to run it on your machine

### 1. Set up the database
```bash
mysql -u root -p < sql/schema.sql
mysql -u root -p < sql/seed_n5.sql
```
Edit `src/main/java/com/questiu/scheduler/dao/DatabaseConnection.java` if your MySQL
root password isn't blank.

### 2. Build with Maven (needs internet access to Maven Central)
```bash
mvn clean compile
```
This pulls down `ortools-java`, `mysql-connector-j`, and JavaFX - none of which
were reachable in the sandbox I built this in, so this is the first real
compile-check of `SchedulingEngine.java` and `MainApp.java`.

### 3. Run the console pipeline (DB -> solver -> payroll -> print)
```bash
mvn exec:java -Dexec.mainClass=com.questiu.scheduler.Main
```
Expected output: OPTIMAL status, 0 unfilled slots, a payroll line per employee.
If you see errors here, they'll almost certainly be either (a) OR-Tools Java API
signature differences between versions - check the version in `pom.xml` against
what's on Maven Central - or (b) the MySQL password in `DatabaseConnection.java`.

### 4. Run the JavaFX UI
```bash
mvn javafx:run
```
Click "Generate Roster & Payroll". It runs the exact same pipeline as step 3
and displays the result in a table.

### 5. (Optional) Re-run the Python validation
```bash
pip install ortools mysql-connector-python
python3 python_validation/solver_prototype.py
```
This is what I used to validate the constraint model before porting it to Java -
keep it as a reference / sanity-check if the Java version ever gives a
surprising result.

## Known simplifications in this FYP1 build (to fix in FYP2)

- The CP-SAT objective currently only minimises unfilled demand. The full
  weighted objective from Section 3.4.4 (overtime cost, workload imbalance,
  preference violations, labour cost) still needs to be added - the
  `lambda_1..4` weights are already in the `objective_weight` table, just not
  wired into `SchedulingEngine.java` yet.
- Role-matching and hard-hour-cap constraints are implemented; preference
  soft-constraints (the `preference` table) are not yet read by the solver.
- `MainApp.java` only has a "Generate" button + output table - no data-entry
  screens yet for adding employees/shifts (Section 3.7, next iteration).
- No persistence of results back to `assignment` / `payroll_record` /
  `constraint_violation` tables yet - `Main.java` and `MainApp.java` currently
  only print/display, they don't write back to MySQL.

## File map

```
sme-scheduler/
  pom.xml                          Maven build (OR-Tools, MySQL connector, JavaFX)
  sql/schema.sql                   14-table DDL - tested, runs clean
  sql/seed_n5.sql                  n=5 synthetic dataset - tested, loads clean
  python_validation/solver_prototype.py   Validated CP-SAT logic (reference)
  src/main/java/com/questiu/scheduler/
    model/                         Employee, Shift, Availability, RosterAssignment, PayrollRecord
    dao/                           JDBC data access - tested against real DB
    payroll/PayrollCalculator.java Section 3.5 formula - tested, matches Table 3.1
    solver/SchedulingEngine.java   CP-SAT model (Section 3.4) - compile-test this first
    ui/MainApp.java                JavaFX skeleton - compile-test this first
    Main.java                      CLI pipeline runner (DB -> solver -> payroll)
  src/test/java/com/questiu/scheduler/
    PayrollCalculatorManualTest.java   Ran - PASS
    DaoIntegrationTest.java            Ran - PASS
```
