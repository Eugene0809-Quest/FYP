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
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Constraint-based scheduling engine (Section 3.4), implemented with the
 * OR-Tools CP-SAT solver (Section 3.4.5).
 *
 * Upgraded to implement the full weighted objective function Z from Section 3.4.4:
 * Minimizing a weighted combination of unfilled shifts (lambda_1), labor cost (lambda_2),
 * and overtime hours (lambda_3).
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

    public SolveResult solve(List<Employee> employees, List<Shift> shifts,
                             List<Availability> availability, double maxSolveTimeSeconds) {

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

        // ---- Objective Z: Full Weighted Multi-Objective (Section 3.4.4) ----
        LinearExprBuilder objective = LinearExpr.newBuilder();

        // 1. Unfilled shifts penalty (lambda_1 dominant weight to ensure coverage first)
        for (IntVar u : unfilled.values()) {
            objective.addTerm(u, 10000); 
        }

        // Track employee hours for cost and overtime minimization
        for (Employee e : employees) {
            // 2. Overtime penalty (lambda_3): standard threshold is 40 hours (4000 hundredths)
            IntVar otVar = model.newIntVar(0, 5000, "ot_e" + e.getEmployeeId());
            
            LinearExprBuilder otConstraint = LinearExpr.newBuilder();
            for (Shift s : shifts) {
                long coeff = Math.round(s.durationHours() * 100);
                otConstraint.addTerm(x.get(key(e.getEmployeeId(), s.getShiftId())), coeff);
            }
            otConstraint.addTerm(otVar, -1);
            model.addLessOrEqual(otConstraint, 4000);
            
            objective.addTerm(otVar, 100); // Weight for overtime hours

            // 3. Labor cost proxy penalty (lambda_2): proportional to total hours worked
            for (Shift s : shifts) {
                long costCoeff = Math.round(s.durationHours() * 10);
                objective.addTerm(x.get(key(e.getEmployeeId(), s.getShiftId())), costCoeff);
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