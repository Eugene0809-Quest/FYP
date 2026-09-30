package com.questiu.scheduler;

import com.questiu.scheduler.model.Availability;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.EmploymentType;
import com.questiu.scheduler.model.Shift;
import com.questiu.scheduler.solver.SchedulingEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for objective priorities in SchedulingEngine.
 * Guards the fix where the overtime term's flat 40h threshold outweighed the
 * unfilled-demand penalty and made the solver leave shifts unfilled for
 * full-time staff who were still within their 45h cap.
 */
class SchedulingEngineCoverageTest {

    private static final LocalTime LUNCH_START = LocalTime.of(11, 0);
    private static final LocalTime LUNCH_END = LocalTime.of(15, 0);
    private static final LocalTime DINNER_START = LocalTime.of(18, 0);
    private static final LocalTime DINNER_END = LocalTime.of(22, 0);

    private static Employee employee(int id, String maxHours, EmploymentType type) {
        return new Employee(id, "Emp" + id, 1, new BigDecimal("12.00"),
                new BigDecimal(maxHours), true, "1234567890", type);
    }

    /** Available 11:00-22:00 every day, so any lunch/dinner shift is coverable. */
    private static List<Availability> alwaysAvailable(int employeeId) {
        List<Availability> list = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            list.add(new Availability(employeeId, day, LocalTime.of(11, 0), LocalTime.of(22, 0)));
        }
        return list;
    }

    /** n four-hour shifts, one person each: lunches Mon-Sun first, then dinners Mon onwards. */
    private static List<Shift> fourHourShifts(int n) {
        List<Shift> shifts = new ArrayList<>();
        int id = 1;
        for (int day = 1; day <= 7 && shifts.size() < n; day++) {
            shifts.add(new Shift(id++, day, LUNCH_START, LUNCH_END, 1, 1));
        }
        for (int day = 1; day <= 7 && shifts.size() < n; day++) {
            shifts.add(new Shift(id++, day, DINNER_START, DINNER_END, 1, 1));
        }
        return shifts;
    }

    @Test
    void fullTimeEmployeeCoversAllShiftsUpToFortyFiveHourCap() {
        // 11 shifts x 4h = 44h: above the old 40h soft threshold, within the 45h cap.
        Employee e = employee(1, "45.00", EmploymentType.FULL_TIME);
        SchedulingEngine.SolveResult r = new SchedulingEngine()
                .solve(List.of(e), fourHourShifts(11), alwaysAvailable(1), 10.0);

        assertTrue(r.isFeasible());
        assertEquals(0, r.totalUnfilled, "coverage must outrank the soft overtime threshold");
        assertEquals(11, r.assignments.size());
    }

    @Test
    void hardCapStillLeavesExcessShiftsUnfilled() {
        // Part-time cap 30h: at most 7 four-hour shifts (28h); the 8th must stay unfilled.
        Employee e = employee(1, "30.00", EmploymentType.PART_TIME);
        SchedulingEngine.SolveResult r = new SchedulingEngine()
                .solve(List.of(e), fourHourShifts(8), alwaysAvailable(1), 10.0);

        assertTrue(r.isFeasible());
        assertEquals(1, r.totalUnfilled);
        assertEquals(7, r.assignments.size());
    }
}
