package com.questiu.scheduler.solver;

import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks a manually-built roster (Section 3.7 manual assignment screen)
 * against the two hard constraints that ManualAssignmentPane's eligible-
 * employee filtering cannot enforce by construction - NO_OVERLAP and
 * MAX_HOURS (Section 3.4) - since both depend on the *combination* of
 * picks across all slots, not any single slot in isolation. Used to
 * surface compliance warnings (Section 3.10) rather than block submission,
 * since a human override channel is the point of manual mode.
 *
 * It also reports a LEAVE warning when someone is assigned on a day they are
 * on approved leave (FYP2). Leave depends on the week being rostered, which
 * the slot dropdowns cannot know when they are built, so it is checked here
 * as a safety net even though the dropdowns also hide people on leave.
 */
public class ManualScheduleValidator {

    public static class ValidationResult {
        public final List<String> violations;
        public final int unfilledSlots;
        public final int totalSlots;

        ValidationResult(List<String> violations, int unfilledSlots, int totalSlots) {
            this.violations = violations;
            this.unfilledSlots = unfilledSlots;
            this.totalSlots = totalSlots;
        }

        public boolean isClean() { return violations.isEmpty(); }

        public double coveragePercent() {
            return totalSlots == 0 ? 100.0 : 100.0 * (totalSlots - unfilledSlots) / totalSlots;
        }
    }

    /** Same checks as before, with no leave information (kept so existing callers and tests still work). */
    public ValidationResult validate(List<Employee> employees, List<Shift> shifts,
                                      List<RosterAssignment> assignments) {
        return validate(employees, shifts, assignments, Map.of(), null);
    }

    /**
     * @param leaveDays employeeId -> days of the roster week (1 = Monday ... 7 = Sunday) on approved leave,
     *                  as produced by LeaveAvailabilityFilter.leaveDaysByEmployee
     * @param weekStart the Monday of the roster week; used to print the calendar date in the warning
     */
    public ValidationResult validate(List<Employee> employees, List<Shift> shifts,
                                      List<RosterAssignment> assignments,
                                      Map<Integer, Set<Integer>> leaveDays, LocalDate weekStart) {
        Map<Integer, Employee> employeesById = employees.stream()
                .collect(Collectors.toMap(Employee::getEmployeeId, e -> e));
        Map<Integer, Shift> shiftsById = shifts.stream()
                .collect(Collectors.toMap(Shift::getShiftId, s -> s));

        List<String> violations = new ArrayList<>();

        int totalSlots = shifts.stream().mapToInt(Shift::getStaffNeeded).sum();
        int unfilled = Math.max(0, totalSlots - assignments.size());

        // Group each employee's assigned shifts so overlap/hours can be checked across all of them.
        Map<Integer, List<Shift>> shiftsByEmployee = new HashMap<>();
        for (RosterAssignment a : assignments) {
            shiftsByEmployee.computeIfAbsent(a.getEmployeeId(), k -> new ArrayList<>())
                    .add(shiftsById.get(a.getShiftId()));
        }

        // NO_OVERLAP: same employee, two assigned shifts whose times overlap.
        for (Map.Entry<Integer, List<Shift>> entry : shiftsByEmployee.entrySet()) {
            Employee emp = employeesById.get(entry.getKey());
            List<Shift> empShifts = entry.getValue();
            for (int i = 0; i < empShifts.size(); i++) {
                for (int j = i + 1; j < empShifts.size(); j++) {
                    if (empShifts.get(i).overlapsWith(empShifts.get(j))) {
                        violations.add(String.format(
                                "%s is double-booked: shift #%d and shift #%d overlap.",
                                emp.getFullName(), empShifts.get(i).getShiftId(), empShifts.get(j).getShiftId()));
                    }
                }
            }
        }

        // MAX_HOURS: total assigned hours per employee vs. their own max_hours_week.
        for (Map.Entry<Integer, List<Shift>> entry : shiftsByEmployee.entrySet()) {
            Employee emp = employeesById.get(entry.getKey());
            double totalHours = entry.getValue().stream().mapToDouble(Shift::durationHours).sum();
            if (totalHours > emp.getMaxWeeklyHours().doubleValue()) {
                violations.add(String.format(
                        "%s is scheduled for %.1f hours, exceeding their %.1f-hour weekly cap.",
                        emp.getFullName(), totalHours, emp.getMaxWeeklyHours().doubleValue()));
            }
        }

        // LEAVE: an assignment on a day the employee has approved leave.
        if (leaveDays != null && !leaveDays.isEmpty() && weekStart != null) {
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            for (RosterAssignment a : assignments) {
                Shift shift = shiftsById.get(a.getShiftId());
                if (leaveDays.getOrDefault(a.getEmployeeId(), Set.of()).contains(shift.getDayOfWeek())) {
                    Employee emp = employeesById.get(a.getEmployeeId());
                    LocalDate date = weekStart.plusDays(shift.getDayOfWeek() - 1L);
                    violations.add(String.format(
                            "%s is on approved leave on %s %s but is assigned to shift #%d (%s-%s).",
                            emp.getFullName(),
                            date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                            date.format(dateFmt), shift.getShiftId(),
                            shift.getStartTime(), shift.getEndTime()));
                }
            }
        }

        return new ValidationResult(violations, unfilled, totalSlots);
    }
}
