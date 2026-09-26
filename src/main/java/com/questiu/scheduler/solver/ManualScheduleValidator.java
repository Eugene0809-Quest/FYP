package com.questiu.scheduler.solver;

import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Checks a manually-built roster (Section 3.7 manual assignment screen)
 * against the two hard constraints that ManualAssignmentPane's eligible-
 * employee filtering cannot enforce by construction - NO_OVERLAP and
 * MAX_HOURS (Section 3.4) - since both depend on the *combination* of
 * picks across all slots, not any single slot in isolation. Used to
 * surface compliance warnings (Section 3.10) rather than block submission,
 * since a human override channel is the point of manual mode.
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

    public ValidationResult validate(List<Employee> employees, List<Shift> shifts,
                                      List<RosterAssignment> assignments) {
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

        return new ValidationResult(violations, unfilled, totalSlots);
    }
}
