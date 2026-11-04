package com.questiu.scheduler.solver;

import com.questiu.scheduler.model.Availability;
import com.questiu.scheduler.model.LeaveRequest;
import com.questiu.scheduler.model.LeaveStatus;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Connects approved leave to the roster (FYP2). An employee's availability is
 * a recurring weekly window per day of the week; leave is for real calendar
 * dates. For the week being rostered, this works out which days of that week
 * each employee is on approved leave and removes their availability window
 * for those days.
 *
 * The CP-SAT model is NOT changed: its availability rule (C1) already forbids
 * assigning an employee to a shift on a day where they have no availability
 * window, so removing the window is enough to keep them off those shifts.
 *
 * Day numbers follow the rest of the app: 1 = Monday ... 7 = Sunday, and
 * weekStart must be the Monday of the roster week.
 */
public final class LeaveAvailabilityFilter {

    private LeaveAvailabilityFilter() {}

    /** employeeId -> the days of the roster week (1-7) on which they are on approved leave. */
    public static Map<Integer, Set<Integer>> leaveDaysByEmployee(List<LeaveRequest> approvedLeave,
                                                                 LocalDate weekStart) {
        Map<Integer, Set<Integer>> result = new HashMap<>();
        LocalDate weekEnd = weekStart.plusDays(6);
        for (LeaveRequest leave : approvedLeave) {
            if (leave.getStatus() != LeaveStatus.APPROVED) continue;
            LocalDate from = leave.getStartDate().isBefore(weekStart) ? weekStart : leave.getStartDate();
            LocalDate to = leave.getEndDate().isAfter(weekEnd) ? weekEnd : leave.getEndDate();
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                result.computeIfAbsent(leave.getEmployeeId(), k -> new TreeSet<>())
                      .add(day.getDayOfWeek().getValue());
            }
        }
        return result;
    }

    /** A copy of the availability list without the windows that fall on an employee's leave days. */
    public static List<Availability> apply(List<Availability> availability,
                                           Map<Integer, Set<Integer>> leaveDays) {
        return availability.stream()
                .filter(a -> !leaveDays.getOrDefault(a.getEmployeeId(), Set.of()).contains(a.getDayOfWeek()))
                .collect(Collectors.toList());
    }

    /** Total number of employee-days on leave, for the status line (two people off on one day = 2). */
    public static int countEmployeeDays(Map<Integer, Set<Integer>> leaveDays) {
        return leaveDays.values().stream().mapToInt(Set::size).sum();
    }
}
