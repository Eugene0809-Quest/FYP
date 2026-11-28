package com.questiu.scheduler.solver;

import com.questiu.scheduler.model.Availability;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Independent audit of a finished roster (automatic OR manual) against the hard rules of
 * Section 3.4, used for the compliance report (Section 3.10). It reads only the roster and
 * the input data, never the solver, so for an automatic roster it is a second opinion on
 * the solver: the number of hard violations must be zero.
 *
 * Rules (the codes match the rule_code values in the constraint_rule table):
 *  - AVAILABILITY  assigned outside every availability window of that day (hard)
 *  - ROLE_MATCH    employee's role is not the role the shift needs (hard)
 *  - NO_OVERLAP    same employee on two shifts that overlap, or on the same shift twice (hard)
 *  - MAX_HOURS     weekly hours above the employee's own cap (hard)
 *  - LEAVE         assigned on a day of approved leave (hard); reported instead of
 *                  AVAILABILITY for that assignment so it is not counted twice
 *  - COVERAGE      more people on a shift than it needs (hard)
 *  - REST_DAY      rostered on all seven days, so no weekly rest day (hard)
 *  - UNFILLED      fewer people on a shift than it needs (soft: the roster is still usable,
 *                  the shortfall is reported)
 */
public final class ComplianceChecker {

    public enum Rule {
        AVAILABILITY(true), ROLE_MATCH(true), NO_OVERLAP(true), MAX_HOURS(true),
        LEAVE(true), COVERAGE(true), REST_DAY(true), UNFILLED(false);

        private final boolean hard;

        Rule(boolean hard) { this.hard = hard; }

        public boolean isHard() { return hard; }

        /** Value of constraint_rule.rule_code. */
        public String code() { return name(); }
    }

    /** One problem found in a roster. employeeId / shiftId are null when it is not about one person / shift. */
    public static final class Violation {
        public final Rule rule;
        public final Integer employeeId;
        public final Integer shiftId;
        /** How big the problem is: excess hours for MAX_HOURS, missing or extra people for coverage, otherwise 1. */
        public final double severity;
        public final String description;

        Violation(Rule rule, Integer employeeId, Integer shiftId, double severity, String description) {
            this.rule = rule;
            this.employeeId = employeeId;
            this.shiftId = shiftId;
            this.severity = severity;
            this.description = description.length() <= 255 ? description : description.substring(0, 252) + "...";
        }
    }

    public static final class Report {
        public final List<Violation> violations;
        /** Violations of hard rules; must be 0 for a roster the solver produced. */
        public final int hardViolations;
        /** Every row: hard violations plus UNFILLED shortfalls. */
        public final int totalViolations;
        public final int totalPositions;
        public final int unfilledPositions;

        Report(List<Violation> violations, int totalPositions, int unfilledPositions) {
            this.violations = violations;
            this.hardViolations = (int) violations.stream().filter(v -> v.rule.isHard()).count();
            this.totalViolations = violations.size();
            this.totalPositions = totalPositions;
            this.unfilledPositions = unfilledPositions;
        }

        public double coveragePercent() {
            return totalPositions == 0 ? 100.0 : 100.0 * (totalPositions - unfilledPositions) / totalPositions;
        }

        public boolean isCompliant() { return hardViolations == 0; }
    }

    private ComplianceChecker() {}

    /**
     * @param availability the employees' own availability (before leave is removed from it)
     * @param leaveDays    employeeId -> days of the roster week (1 = Monday ... 7 = Sunday) on approved leave;
     *                     may be null or empty
     * @param weekStart    the Monday of the roster week, only used to print dates; may be null
     */
    public static Report check(List<Employee> employees, List<Shift> shifts, List<Availability> availability,
                               List<RosterAssignment> assignments, Map<Integer, Set<Integer>> leaveDays,
                               LocalDate weekStart) {
        Map<Integer, Employee> employeesById = employees.stream()
                .collect(Collectors.toMap(Employee::getEmployeeId, e -> e, (a, b) -> a));
        Map<Integer, Shift> shiftsById = shifts.stream()
                .collect(Collectors.toMap(Shift::getShiftId, s -> s, (a, b) -> a));
        Map<Integer, List<Availability>> availabilityByEmployee = availability.stream()
                .collect(Collectors.groupingBy(Availability::getEmployeeId));

        List<Violation> found = new ArrayList<>();
        Map<Integer, List<Shift>> shiftsByEmployee = new LinkedHashMap<>();
        Map<Integer, Integer> assignedCount = new HashMap<>();

        for (RosterAssignment a : assignments) {
            Employee e = employeesById.get(a.getEmployeeId());
            Shift s = shiftsById.get(a.getShiftId());
            if (e == null || s == null) continue; // not about a known employee/shift: nothing to judge
            shiftsByEmployee.computeIfAbsent(e.getEmployeeId(), k -> new ArrayList<>()).add(s);
            assignedCount.merge(s.getShiftId(), 1, Integer::sum);

            if (e.getRoleId() != s.getRequiredRoleId()) {
                found.add(new Violation(Rule.ROLE_MATCH, e.getEmployeeId(), s.getShiftId(), 1,
                        e.getFullName() + " does not have the role needed for " + describe(s, weekStart) + "."));
            }
            boolean onLeave = leaveDays != null
                    && leaveDays.getOrDefault(e.getEmployeeId(), Set.of()).contains(s.getDayOfWeek());
            if (onLeave) {
                found.add(new Violation(Rule.LEAVE, e.getEmployeeId(), s.getShiftId(), 1,
                        e.getFullName() + " is on approved leave but is assigned to " + describe(s, weekStart) + "."));
            } else if (availabilityByEmployee.getOrDefault(e.getEmployeeId(), List.of())
                    .stream().noneMatch(w -> w.covers(s))) {
                found.add(new Violation(Rule.AVAILABILITY, e.getEmployeeId(), s.getShiftId(), 1,
                        e.getFullName() + " is not available for " + describe(s, weekStart) + "."));
            }
        }

        for (Map.Entry<Integer, List<Shift>> entry : shiftsByEmployee.entrySet()) {
            Employee e = employeesById.get(entry.getKey());
            List<Shift> mine = entry.getValue();
            for (int i = 0; i < mine.size(); i++) {
                for (int j = i + 1; j < mine.size(); j++) {
                    if (mine.get(i).overlapsWith(mine.get(j))) {
                        found.add(new Violation(Rule.NO_OVERLAP, e.getEmployeeId(), mine.get(i).getShiftId(), 1,
                                e.getFullName() + " is double-booked: " + describe(mine.get(i), weekStart)
                                        + " overlaps " + describe(mine.get(j), weekStart) + "."));
                    }
                }
            }
            long daysWorked = mine.stream().map(Shift::getDayOfWeek).distinct().count();
            if (daysWorked > WorkingDayRule.MAX_WORKING_DAYS_PER_WEEK) {
                found.add(new Violation(Rule.REST_DAY, e.getEmployeeId(), null,
                        daysWorked - WorkingDayRule.MAX_WORKING_DAYS_PER_WEEK,
                        e.getFullName() + " is rostered on " + daysWorked
                                + " different days; at least one rest day a week is required."));
            }
            double hours = mine.stream().mapToDouble(Shift::durationHours).sum();
            double cap = e.getMaxWeeklyHours().doubleValue();
            if (hours > cap + 1e-9) {
                found.add(new Violation(Rule.MAX_HOURS, e.getEmployeeId(), null,
                        Math.round((hours - cap) * 100) / 100.0,
                        String.format(Locale.ROOT, "%s works %.1f hours, above the %.1f-hour weekly cap.",
                                e.getFullName(), hours, cap)));
            }
        }

        int totalPositions = 0;
        int unfilled = 0;
        for (Shift s : shifts) {
            int needed = s.getStaffNeeded();
            int have = assignedCount.getOrDefault(s.getShiftId(), 0);
            totalPositions += needed;
            if (have > needed) {
                found.add(new Violation(Rule.COVERAGE, null, s.getShiftId(), have - needed,
                        describe(s, weekStart) + " has " + have + " people but needs " + needed + "."));
            } else if (have < needed) {
                unfilled += needed - have;
                found.add(new Violation(Rule.UNFILLED, null, s.getShiftId(), needed - have,
                        describe(s, weekStart) + " has " + have + " of " + needed + " people needed."));
            }
        }
        return new Report(found, totalPositions, unfilled);
    }

    /** e.g. "shift #12 (Mon 02/11/2026, 11:00-15:00)". */
    private static String describe(Shift s, LocalDate weekStart) {
        String day = DayOfWeek.of(s.getDayOfWeek()).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        String date = weekStart == null ? ""
                : " " + weekStart.plusDays(s.getDayOfWeek() - 1L).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return "shift #" + s.getShiftId() + " (" + day + date + ", " + s.getStartTime() + "-" + s.getEndTime() + ")";
    }
}
