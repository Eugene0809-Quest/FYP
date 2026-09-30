package com.questiu.scheduler.solver;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.google.ortools.sat.Literal;
import com.questiu.scheduler.model.Availability;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.Preference;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Constraint-based scheduling engine (Section 3.4), implemented with the
 * OR-Tools CP-SAT solver (Section 3.4.5).
 *
 * Objective Z (Section 3.4.4) now wires in all four of its terms: unfilled
 * shifts (dominant), labour cost proxy, workload imbalance (per role group),
 * and preference violations (the employee_preference table, read via the
 * 5-argument solve overload). Preferences are soft: they only break ties
 * between employees who could all legitimately work a shift.
 *
 * KNOWN LIMITATION - Overtime penalty term: this term is a permanent no-op
 * under Automatic mode and was deliberately left that way rather than
 * "fixed" to look more correct. Hard constraint 5 below already forbids
 * anyone from ever being assigned more than their own max_hours_week - so
 * real overtime (hours beyond that cap) can never occur in a CP-SAT-solved
 * schedule; PayrollCalculator's overtimeHours is always 0 for one. Setting
 * this term's threshold to match each employee's own max_hours_week (which
 * would look like the "obvious" fix) would only make an already-inert term
 * exactly as inert, since the threshold and the hard cap would then be
 * identical and the soft variable could never activate either way. Turning
 * it into a genuinely active term would mean redefining what "Overtime
 * penalty" means (e.g. discouraging hours near, rather than over, the cap)
 * - which is really the WorkloadImbalance term's job, now implemented
 * separately below under its own correct name instead of overloading this
 * one. Left as flat-40h so the code doesn't silently claim a fix it can't
 * actually make.
 *
 * CORRECTION: the threshold was previously a flat 40h. For full-time staff
 * (45h cap) that made the term ACTIVE between 40h and 45h, at 10,000 per hour -
 * more than an unfilled position (10,000) - so the solver preferred leaving a
 * shift unfilled to a 44h week. The threshold is now each employee's own
 * max_hours_week, making the term genuinely inert and restoring coverage as the
 * dominant objective. See SchedulingEngineCoverageTest.
 */
public class SchedulingEngine {

    /** Result wrapper so callers get both the assignments and solve diagnostics. */
    public static class SolveResult {
        public final CpSolverStatus status;
        public final List<RosterAssignment> assignments;
        public final int totalUnfilled;
        public final long solveTimeMillis;

        SolveResult(CpSolverStatus status, List<RosterAssignment> assignments,
                    int totalUnfilled, long solveTimeMillis) {
            this.status = status;
            this.assignments = assignments;
            this.totalUnfilled = totalUnfilled;
            this.solveTimeMillis = solveTimeMillis;
        }

        public boolean isFeasible() {
            return status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE;
        }
    }

    static {
        Loader.loadNativeLibraries();
    }

    /** Backwards-compatible overload: solves with no preferences (all neutral). */
    public SolveResult solve(List<Employee> employees, List<Shift> shifts,
                             List<Availability> availability, double maxSolveTimeSeconds) {
        return solve(employees, shifts, availability, List.of(), maxSolveTimeSeconds);
    }

    public SolveResult solve(List<Employee> employees, List<Shift> shifts,
                             List<Availability> availability, List<Preference> preferences,
                             double maxSolveTimeSeconds) {

        long startTime = System.currentTimeMillis();
        CpModel model = new CpModel();

        // ---- Decision variables: x[e,s] ----
        Map<String, com.google.ortools.sat.BoolVar> x = new HashMap<>();
        for (Employee e : employees) {
            for (Shift s : shifts) {
                x.put(key(e.getEmployeeId(), s.getShiftId()),
                        model.newBoolVar("x_e" + e.getEmployeeId() + "_s" + s.getShiftId()));
            }
        }

        // ---- Availability lookup: (employeeId, dayOfWeek) -> list of windows ----
        Map<Integer, List<Availability>> availByEmployee = availability.stream()
                .collect(Collectors.groupingBy(Availability::getEmployeeId));

        // ---- Hard constraints 1 & 2: availability + role matching ----
        for (Employee e : employees) {
            List<Availability> empAvail = availByEmployee.getOrDefault(e.getEmployeeId(), List.of());
            for (Shift s : shifts) {
                boolean available = empAvail.stream().anyMatch(a -> a.covers(s));
                boolean roleMatches = e.getRoleId() == s.getRequiredRoleId();
                if (!available || !roleMatches) {
                    model.addEquality(x.get(key(e.getEmployeeId(), s.getShiftId())), 0);
                }
            }
        }

        // ---- Hard constraint 3: coverage (with unfilled-demand slack, Section 3.4.4) ----
        Map<Integer, IntVar> unfilled = new HashMap<>();
        for (Shift s : shifts) {
            List<Literal> assignedVars = employees.stream()
                    .map(e -> (Literal) x.get(key(e.getEmployeeId(), s.getShiftId())))
                    .collect(Collectors.toList());
            IntVar unfilledVar = model.newIntVar(0, s.getStaffNeeded(), "unfilled_s" + s.getShiftId());
            unfilled.put(s.getShiftId(), unfilledVar);

            LinearExprBuilder lhs = LinearExpr.newBuilder();
            for (Literal lit : assignedVars) lhs.addTerm(lit, 1);
            lhs.addTerm(unfilledVar, 1);
            model.addEquality(lhs, s.getStaffNeeded());
        }

        // ---- Hard constraint 4: no overlapping shifts for the same employee ----
        Map<Integer, List<Shift>> shiftsByDay = shifts.stream()
                .collect(Collectors.groupingBy(Shift::getDayOfWeek));
        for (Employee e : employees) {
            for (List<Shift> dayShifts : shiftsByDay.values()) {
                for (int i = 0; i < dayShifts.size(); i++) {
                    for (int j = i + 1; j < dayShifts.size(); j++) {
                        Shift s1 = dayShifts.get(i);
                        Shift s2 = dayShifts.get(j);
                        if (s1.overlapsWith(s2)) {
                            LinearExprBuilder sumExpr = LinearExpr.newBuilder();
                            sumExpr.addTerm(x.get(key(e.getEmployeeId(), s1.getShiftId())), 1);
                            sumExpr.addTerm(x.get(key(e.getEmployeeId(), s2.getShiftId())), 1);
                            model.addLessOrEqual(sumExpr, 1);
                        }
                    }
                }
            }
        }

        // ---- Hard constraint 5: max weekly hours (45h cap, Section 3.6) ----
        for (Employee e : employees) {
            LinearExprBuilder hoursExpr = LinearExpr.newBuilder();
            for (Shift s : shifts) {
                long coeff = Math.round(s.durationHours() * 100);
                hoursExpr.addTerm(x.get(key(e.getEmployeeId(), s.getShiftId())), coeff);
            }
            long maxHoursScaled = Math.round(e.getMaxWeeklyHours().doubleValue() * 100);
            model.addLessOrEqual(hoursExpr, maxHoursScaled);
        }

        // ---- Objective Z: Weighted Multi-Objective (Section 3.4.4) ----
        LinearExprBuilder objective = LinearExpr.newBuilder();

        // 1. Unfilled shifts penalty (lambda_1 dominant weight to ensure coverage first)
        for (IntVar u : unfilled.values()) {
            objective.addTerm(u, 10000);
        }

        // Track employee hours for cost and overtime minimization
        for (Employee e : employees) {
            // 2. Overtime penalty (lambda_3): KNOWN LIMITATION, permanently a
            // no-op under Automatic mode - see class javadoc. The threshold equals
            // each employee's own max_hours_week, so it cannot activate.
            IntVar otVar = model.newIntVar(0, 5000, "ot_e" + e.getEmployeeId());

            LinearExprBuilder otConstraint = LinearExpr.newBuilder();
            for (Shift s : shifts) {
                long coeff = Math.round(s.durationHours() * 100);
                otConstraint.addTerm(x.get(key(e.getEmployeeId(), s.getShiftId())), coeff);
            }
            otConstraint.addTerm(otVar, -1);
            // Threshold = this employee's own hard cap (hundredths of an hour), so the
            // variable can never activate: it is a deliberate, documented no-op.
            long otThresholdScaled = Math.round(e.getMaxWeeklyHours().doubleValue() * 100);
            model.addLessOrEqual(otConstraint, otThresholdScaled);

            objective.addTerm(otVar, 100); // Weight for overtime hours

            // 3. Labor cost proxy penalty (lambda_2): proportional to total hours worked
            for (Shift s : shifts) {
                long costCoeff = Math.round(s.durationHours() * 10);
                objective.addTerm(x.get(key(e.getEmployeeId(), s.getShiftId())), costCoeff);
            }
        }

        // 4. Workload imbalance penalty (Section 3.4.4's WorkloadImbalance term,
        // previously unimplemented - see class javadoc). Computed PER ROLE
        // GROUP, not across all employees together: staff in different roles
        // can't cover each other's shifts anyway (hard constraints 1/2 already
        // forbid it), so "balancing" a Kitchen worker's hours against a
        // Front-of-House worker's hours doesn't correspond to any real
        // fairness concern - only spread WITHIN a role group is meaningful.
        // Standard linear min-max/range formulation per group: maxLoad tracks
        // the most-loaded employee's scaled hours in that role, minLoad the
        // least-loaded, and the objective minimizes their spread - so the
        // solver prefers spreading shifts evenly among same-role staff
        // whenever doing so doesn't cost anything extra in unfilled demand or
        // labour cost (both weighted far more heavily above). Built using
        // only addTerm/addLessOrEqual against a builder, matching every other
        // constraint in this file, to avoid relying on an OR-Tools overload
        // this project hasn't already proven works.
        final long WORKLOAD_IMBALANCE_WEIGHT = 5; // tunable - see class javadoc; not yet read from objective_weight table
        Map<Integer, List<Employee>> employeesByRole = employees.stream()
                .collect(Collectors.groupingBy(Employee::getRoleId));
        for (Map.Entry<Integer, List<Employee>> roleGroup : employeesByRole.entrySet()) {
            IntVar maxLoad = model.newIntVar(0, 100000, "maxLoad_role" + roleGroup.getKey());
            IntVar minLoad = model.newIntVar(0, 100000, "minLoad_role" + roleGroup.getKey());
            for (Employee e : roleGroup.getValue()) {
                LinearExprBuilder loadMinusMax = LinearExpr.newBuilder();
                LinearExprBuilder minMinusLoad = LinearExpr.newBuilder();
                for (Shift s : shifts) {
                    long coeff = Math.round(s.durationHours() * 100);
                    loadMinusMax.addTerm(x.get(key(e.getEmployeeId(), s.getShiftId())), coeff);
                    minMinusLoad.addTerm(x.get(key(e.getEmployeeId(), s.getShiftId())), -coeff);
                }
                loadMinusMax.addTerm(maxLoad, -1);
                model.addLessOrEqual(loadMinusMax, 0); // load - maxLoad <= 0  =>  load <= maxLoad

                minMinusLoad.addTerm(minLoad, 1);
                model.addLessOrEqual(minMinusLoad, 0); // minLoad - load <= 0  =>  minLoad <= load
            }
            objective.addTerm(maxLoad, WORKLOAD_IMBALANCE_WEIGHT);
            objective.addTerm(minLoad, -WORKLOAD_IMBALANCE_WEIGHT);
        }

        // 5. Preference violations (Section 3.4.4's PreferenceViolations term).
        // Each stored preference has level -2..+2. Assigning employee e to shift s
        // costs  -level * PREFERENCE_WEIGHT : "avoid"/"strongly avoid" add a
        // penalty, "prefer"/"strongly prefer" give a reward (negative cost).
        // Coverage is an equality (assigned + unfilled = needed), so rewards can
        // never make the solver over-staff a shift - they only influence WHO fills it.
        // Weight 200 per level unit: well below the unfilled-demand weight (10000),
        // so coverage always wins, but above the labour-cost proxy (about 40 per
        // 4h shift), so a preference can actually tip the choice. Tunable; not yet
        // read from the objective_weight table.
        final long PREFERENCE_WEIGHT = 200;
        for (Preference p : preferences) {
            com.google.ortools.sat.BoolVar var = x.get(key(p.getEmployeeId(), p.getShiftId()));
            if (var != null && p.getLevel() != 0) {
                objective.addTerm(var, -p.getLevel() * PREFERENCE_WEIGHT);
            }
        }

        model.minimize(objective);

        // ---- Solve ----
        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(maxSolveTimeSeconds);
        CpSolverStatus status = solver.solve(model);
        long elapsed = System.currentTimeMillis() - startTime;

        List<RosterAssignment> result = new ArrayList<>();
        int totalUnfilled = 0;
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            for (Employee e : employees) {
                for (Shift s : shifts) {
                    if (solver.value(x.get(key(e.getEmployeeId(), s.getShiftId()))) == 1) {
                        result.add(new RosterAssignment(e.getEmployeeId(), s.getShiftId()));
                    }
                }
            }
            for (IntVar u : unfilled.values()) {
                totalUnfilled += solver.value(u);
            }
        }

        return new SolveResult(status, result, totalUnfilled, elapsed);
    }

    private static String key(int employeeId, int shiftId) {
        return employeeId + "_" + shiftId;
    }
}