"""
CP-SAT solver prototype - validates the constraint model from Section 3.4
against real data pulled from the MySQL 'shift_scheduling' database.

This is a validation script, not the final deliverable. Once the model here
is confirmed correct, the same logic is ported to Java (OR-Tools Java API)
in JavaSolverPrototype.java for the actual FYP submission.
"""
import mysql.connector
from ortools.sat.python import cp_model
from collections import defaultdict

conn = mysql.connector.connect(host="localhost", user="root", database="shift_scheduling")
cur = conn.cursor(dictionary=True)

# ---- Load data ----
cur.execute("SELECT * FROM employee")
employees = cur.fetchall()

cur.execute("SELECT * FROM shift WHERE week_plan_id = 1")
shifts = cur.fetchall()

cur.execute("SELECT * FROM availability WHERE week_plan_id = 1")
availability = cur.fetchall()

print(f"Loaded {len(employees)} employees, {len(shifts)} shifts, {len(availability)} availability rows")

# Build availability lookup: (employee_id, day_of_week) -> (start, end)
avail_map = {}
for a in availability:
    avail_map[(a["employee_id"], a["day_of_week"])] = (a["start_time"], a["end_time"])

def shift_duration_hours(s):
    # start_time/end_time come back as timedelta in mysql-connector
    total_seconds = (s["end_time"] - s["start_time"]).total_seconds()
    return total_seconds / 3600.0

def employee_available(emp_id, shift):
    key = (emp_id, shift["day_of_week"])
    if key not in avail_map:
        return False
    start, end = avail_map[key]
    return start <= shift["start_time"] and shift["end_time"] <= end

# ---- Build CP-SAT model ----
model = cp_model.CpModel()

x = {}  # x[e, s] = 1 if employee e assigned to shift s
for e in employees:
    for s in shifts:
        x[e["employee_id"], s["shift_id"]] = model.NewBoolVar(f'x_e{e["employee_id"]}_s{s["shift_id"]}')

# Hard constraint 1: availability - can only assign if available AND role matches
for e in employees:
    for s in shifts:
        var = x[e["employee_id"], s["shift_id"]]
        if not employee_available(e["employee_id"], s) or e["role_id"] != s["required_role_id"]:
            model.Add(var == 0)

# Hard constraint 2: coverage - each shift must have exactly staff_needed assigned
# (if infeasible to fully cover, we allow shortfall via a slack var penalised heavily -
#  this matches Section 3.4.4's UnfilledDemand term so the solver still returns a result)
unfilled = {}
for s in shifts:
    assigned_sum = sum(x[e["employee_id"], s["shift_id"]] for e in employees)
    unfilled[s["shift_id"]] = model.NewIntVar(0, s["staff_needed"], f'unfilled_s{s["shift_id"]}')
    model.Add(assigned_sum + unfilled[s["shift_id"]] == s["staff_needed"])

# Hard constraint 3: no overlapping shifts for the same employee (same day, overlapping times)
shifts_by_day = defaultdict(list)
for s in shifts:
    shifts_by_day[s["day_of_week"]].append(s)

for e in employees:
    for day, day_shifts in shifts_by_day.items():
        for i in range(len(day_shifts)):
            for j in range(i + 1, len(day_shifts)):
                s1, s2 = day_shifts[i], day_shifts[j]
                overlap = s1["start_time"] < s2["end_time"] and s2["start_time"] < s1["end_time"]
                if overlap:
                    model.Add(x[e["employee_id"], s1["shift_id"]] + x[e["employee_id"], s2["shift_id"]] <= 1)

# Hard constraint 4: max weekly hours (45h, Section 3.6)
emp_hours = {}
for e in employees:
    total_hours_expr = sum(
        int(round(shift_duration_hours(s) * 100)) * x[e["employee_id"], s["shift_id"]]
        for s in shifts
    )
    emp_hours[e["employee_id"]] = total_hours_expr
    max_hundredths = int(round(float(e["max_weekly_hours"]) * 100))
    # allow overtime up to +... actually hard cap per report: total hours must not exceed max
    model.Add(total_hours_expr <= max_hundredths)

# ---- Objective: minimise unfilled demand (dominant) + a light preference for fewer total assigned hours variance ----
total_unfilled = sum(unfilled.values())
model.Minimize(total_unfilled * 1000)

solver = cp_model.CpSolver()
solver.parameters.max_time_in_seconds = 10
status = solver.Solve(model)

print(f"Solve status: {solver.StatusName(status)}")
print(f"Total unfilled slots: {solver.Value(total_unfilled)}")
print()

if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
    emp_names = {e["employee_id"]: e["full_name"] for e in employees}
    emp_rates = {e["employee_id"]: float(e["hourly_rate"]) for e in employees}

    print("=== Sample of assigned shifts (Day 1, Monday) ===")
    for s in shifts:
        if s["day_of_week"] == 1:
            assigned = [emp_names[e["employee_id"]] for e in employees if solver.Value(x[e["employee_id"], s["shift_id"]])]
            print(f"  Shift {s['shift_id']} ({s['start_time']}-{s['end_time']}, role {s['required_role_id']}, need {s['staff_needed']}): {assigned}")

    print()
    print("=== Weekly hours & payroll per employee (Section 3.5 formula) ===")
    for e in employees:
        eid = e["employee_id"]
        total_h = sum(shift_duration_hours(s) for s in shifts if solver.Value(x[eid, s["shift_id"]]))
        max_h = float(e["max_weekly_hours"])
        regular_h = min(total_h, max_h)
        ot_h = max(0.0, total_h - max_h)
        rate = emp_rates[eid]
        regular_pay = rate * regular_h
        ot_pay = rate * 1.5 * ot_h
        total_pay = regular_pay + ot_pay
        print(f"  {emp_names[eid]:8s} | hours={total_h:5.1f} | regular={regular_h:5.1f} | OT={ot_h:4.1f} "
              f"| RM{regular_pay:7.2f} + RM{ot_pay:6.2f} OT = RM{total_pay:7.2f}")
else:
    print("No feasible solution found.")

cur.close()
conn.close()
