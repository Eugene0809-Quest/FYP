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

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Constraint-based scheduling engine (Section 3.4), implemented with the
 * OR-Tools CP-SAT solver (Section 3.4.5).
 *
 * Objective Z (Section 3.4.4) wires in all four of its terms: unfilled
 * shifts (dominant), labour cost proxy, workload imbalance (per role group),
 * and preference violations (the employee_preference table, read via the
 * 5-argument solve overload). Preferences are soft: they only break ties
 * between employees who could all legitimately work a shift.
 *
 * KNOWN LIMITATIONS:
 * - Overtime penalty term: a deliberate, permanent no-op under Automatic mode.
 *   Hard constraint 5 forbids anyone from being assigned more than their own
 *   max_hours_week, and this term's threshold equals that same cap, so the soft
 *   variable can never activate and PayrollCalculator's overtimeHours is always
 *   0 for a solver-generated roster. (It was previously a flat 40h, which made
 *   it active between 40h and 45h at 10,000 per hour - more than an unfilled
 *   position - so the solver left shifts unfilled instead. See
 *   SchedulingEngineCoverageTest.) Workload spreading is handled separately by
 *   the WorkloadImbalance term.
 * - The labour-cost term is each employee's gross pay for the shift (hourly rate x
 *   hours, doubled on a public holiday), in sen - see LabourCost. It is gross pay
 *   only: employer EPF/SOCSO/EIS are not proportional to gross, so this is close to,
 *   not exactly, the employer's total cost. Callers that do not pass the week start
 *   and holiday dates (the 4- and 5-argument overloads) get no holiday doubling.
 * - Hard constraint 6 gives everyone at least one rest day a week: nobody is rostered on
 *   more than WorkingDayRule.MAX_WORKING_DAYS_PER_WEEK (6) different days.
 * - Workload balance compares only staff who can work at least one shift this week; someone
 *   with no availability (or on leave all week) is not counted as "0 hours".
 * - The objective weights come from SolverWeights (defaults equal the old constants).
 *   They are not yet loaded from the database. They are all read in sen: coverage (an unfilled position, at
 *   least RM10,000) far above everything; then cost, workload spread (RM5 per hour
 *   of difference between the most and least loaded person in a role) and
 *   preferences (RM2 per preference level). A preference therefore only beats a
 *   cost difference smaller than its value, which is a policy choice to tune in FYP2.
 */
public class SchedulingEngine {

    /** Result wrapper so callers get both the assignments and solve diagnostics. */
    public static class SolveResult {
        public final CpSolverStatus status;
        public final List<RosterAssignment> assignments;
        public final int totalUnfilled;
        public final long solveTimeMillis;
        /** The solver's objective value Z in sen; NaN when no roster was found. */
        public final double objectiveValue;

        SolveResult(CpSolverStatus status, List<RosterAssignment> assignments,
                    int totalUnfilled, long solveTimeMillis, double objectiveValue) {
            this.status = status;
            this.assignments = assignments;
            this.totalUnfilled = totalUnfilled;
            this.solveTimeMillis = solveTimeMillis;
            this.objectiveValue = objectiveValue;
        }

        /**
         * Objective value Z in ringgit-equivalent (sen / 100, two decimals), or null when no
         * roster was found. It is wages plus the weighted penalties and rewards, so it is not
         * the payroll: preference rewards can make it lower than the wage bill.
         */
        public Double objectiveValueRm() {
            if (!isFeasible() || Double.isNaN(objectiveValue)) return null;
            return Math.round(objectiveValue) / 100.0;
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

    /** Solves with preferences but without public holidays (no holiday doubling in the cost). */
    public SolveResult solve(List<Employee> employees, List<Shift> shifts,
                             List<Availability> availability, List<Preference> preferences,
                             double maxSolveTimeSeconds) {
        return solve(employees, shifts, availability, preferences, null, Set.of(), maxSolveTimeSeconds);
    }

    /**
     * Full solve. weekStartDate and publicHolidays let the labour-cost term know
     * which shifts are paid at the holiday rate (a shift's calendar date is
     * weekStartDate + dayOfWeek - 1, the same rule PayrollCalculator uses).
     * Pass null / an empty set to ignore holidays.
     */
    public SolveResult solve(List<Employee> employees, List<Shift> shifts,
                             List<Availability> availability, List<Preference> preferences,
                             LocalDate weekStartDate, Set<LocalDate> publicHolidays,
                             double maxSolveTimeSeconds) {
        return solve(employees, shifts, availability, preferences, weekStartDate, publicHolidays,
                SolverWeights.defaults(), maxSolveTimeSeconds);
    }

    /**
     * Full solve with explicit objective weights (see SolverWeights). The weights decide how
     * the solver trades pay, fairness and preferences against each other; they never change
     * the hard constraints, and the price of an unfilled position is kept above the cost of
     * any shift whatever is configured (LabourCost.unfilledPenalty).
     */
    public SolveResult solve(List<Employee> employees, List<Shift> shifts,
                             List<Availability> availability, List<Preference> preferences,
                             LocalDate weekStartDate, Set<LocalDate> publicHolidays,
                             SolverWeights weights, double maxSolveTimeSeconds) {

        String weightProblem = weights.problem();
        if (weightProblem != null) throw new IllegalArgumentException(weightProblem);

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
        // Also notes who can work at least one shift this week: only those people take part in
        // the workload-balance comparison below (see "schedulableIds").
        final Set<Integer> schedulableIds = new HashSet<>();
        final Set<String> eligiblePairs = new HashSet<>(); // "employee_shift" pairs that C1 and C2 allow
        for (Employee e : employees) {
            List<Availability> empAvail = availByEmployee.getOrDefault(e.getEmployeeId(), List.of());
            for (Shift s : shifts) {
                boolean available = empAvail.stream().anyMatch(a -> a.covers(s));
                boolean roleMatches = e.getRoleId() == s.getRequiredRoleId();
                if (!available || !roleMatches) {
                    model.addEquality(x.get(key(e.getEmployeeId(), s.getShiftId())), 0);
                } else {
                    schedulableIds.add(e.getEmployeeId());
                    eligiblePairs.add(key(e.getEmployeeId(), s.getShiftId()));
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

        // ---- Hard constraint 6: at least one rest day per week (Employment Act 1955, s. 59) ----
        // worksThatDay[e,d] = 1 whenever e works any shift on day d; e may work on at most
        // MAX_WORKING_DAYS_PER_WEEK different days. Only people who could work on more days than
        // that need the constraint (for everyone else it can never bind), which keeps the model small.
        for (Employee e : employees) {
            Map<Integer, List<Shift>> eligibleByDay = new TreeMap<>();
            for (Shift s : shifts) {
                if (eligiblePairs.contains(key(e.getEmployeeId(), s.getShiftId()))) {
                    eligibleByDay.computeIfAbsent(s.getDayOfWeek(), dayKey -> new ArrayList<>()).add(s);
                }
            }
            if (eligibleByDay.size() <= WorkingDayRule.MAX_WORKING_DAYS_PER_WEEK) continue;
            LinearExprBuilder daysWorked = LinearExpr.newBuilder();
            for (Map.Entry<Integer, List<Shift>> day : eligibleByDay.entrySet()) {
                com.google.ortools.sat.BoolVar worksThatDay =
                        model.newBoolVar("works_e" + e.getEmployeeId() + "_d" + day.getKey());
                for (Shift s : day.getValue()) {
                    LinearExprBuilder link = LinearExpr.newBuilder();
                    link.addTerm(x.get(key(e.getEmployeeId(), s.getShiftId())), 1);
                    link.addTerm(worksThatDay, -1);
                    model.addLessOrEqual(link, 0); // x[e,s] <= worksThatDay[e,d]
                }
                daysWorked.addTerm(worksThatDay, 1);
            }
            model.addLessOrEqual(daysWorked, WorkingDayRule.MAX_WORKING_DAYS_PER_WEEK);
        }

        // ---- Objective Z: Weighted Multi-Objective (Section 3.4.4) ----
        LinearExprBuilder objective = LinearExpr.newBuilder();

        // Cost of every possible (employee, shift) pair, in sen: rate x hours, doubled on a holiday.
        Map<String, Long> positionCost = new HashMap<>();
        long maxPositionCost = 0;
        for (Employee e : employees) {
            for (Shift s : shifts) {
                boolean holiday = weekStartDate != null && publicHolidays != null
                        && publicHolidays.contains(s.actualDate(weekStartDate));
                long c = weights.scaleCost(LabourCost.positionCostSen(e, s, holiday));
                positionCost.put(key(e.getEmployeeId(), s.getShiftId()), c);
                maxPositionCost = Math.max(maxPositionCost, c);
            }
        }

        // 1. Unfilled shifts penalty (lambda_1 dominant weight to ensure coverage first).
        // Always larger than the cost of filling any position, so the solver never leaves a
        // shift empty just to save wages (see LabourCost.unfilledPenalty).
        long unfilledPenalty = LabourCost.unfilledPenalty(maxPositionCost, weights.unfilledSen());
        for (IntVar u : unfilled.values()) {
            objective.addTerm(u, unfilledPenalty);
        }

        // Per employee: overtime variable (inert) and labour cost
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

            objective.addTerm(otVar, weights.overtimePerHundredthHour()); // Weight for overtime hours

            // 3. Labour cost (lambda_2): the gross pay for each assignment, in sen. Unlike the
            // old hours-proportional proxy this differs between employees (their hourly
            // rates) and between normal and public-holiday shifts, so the solver can now
            // prefer a cheaper roster over a dearer one that covers the same shifts.
            for (Shift s : shifts) {
                String k = key(e.getEmployeeId(), s.getShiftId());
                long costCoeff = positionCost.get(k); // primitive long, like every other addTerm in this file
                objective.addTerm(x.get(k), costCoeff);
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
        final long WORKLOAD_IMBALANCE_WEIGHT = weights.workloadPerHundredthHour(); // RM per hour of spread, see SolverWeights
        // Only staff who can work at least one shift this week are compared. Someone with no
        // availability (a new hire whose availability is not entered yet, or someone on leave
        // all week) would otherwise count as "0 hours" and pin the least-loaded value at 0, so
        // the term stopped rewarding hours for anyone else in the role and a cheap roster could
        // leave a real employee with no work at all.
        Map<Integer, List<Employee>> employeesByRole = employees.stream()
                .filter(e -> schedulableIds.contains(e.getEmployeeId()))
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
        // Default 200 per level unit = RM2.00 (the objective is in sen): far below the
        // unfilled-demand weight, so coverage always wins. Labour cost is real pay, so a
        // preference only tips the choice between employees whose cost for the shift
        // differs by less than it is worth (RM2 per level by default); e.g. it cannot win
        // against a RM28 holiday-pay difference. The value comes from SolverWeights.
        final long PREFERENCE_WEIGHT = weights.preferenceSenPerLevel();
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
        double objectiveValue = Double.NaN;
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            objectiveValue = solver.objectiveValue();
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

        return new SolveResult(status, result, totalUnfilled, elapsed, objectiveValue);
    }

    private static String key(int employeeId, int shiftId) {
        return employeeId + "_" + shiftId;
    }
}
