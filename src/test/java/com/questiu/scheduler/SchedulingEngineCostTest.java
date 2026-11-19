package com.questiu.scheduler;

import com.questiu.scheduler.model.Availability;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.EmploymentType;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;
import com.questiu.scheduler.solver.SchedulingEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The labour-cost term is now each employee's real pay (rate x hours, doubled on a
 * public holiday), so the solver can tell cheaper rosters from dearer ones.
 * These need the OR-Tools native library (run them with mvn test).
 */
class SchedulingEngineCostTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    private static Employee emp(int id, String rate, String maxHours) {
        return new Employee(id, "Emp" + id, 1, new BigDecimal(rate), new BigDecimal(maxHours), true,
                "1234567890", EmploymentType.FULL_TIME);
    }

    private static List<Availability> alwaysAvailable(int... ids) {
        List<Availability> list = new ArrayList<>();
        for (int id : ids) {
            for (int day = 1; day <= 7; day++) {
                list.add(new Availability(id, day, LocalTime.of(11, 0), LocalTime.of(22, 0)));
            }
        }
        return list;
    }

    private static Shift lunch(int id, int day) {
        return new Shift(id, day, LocalTime.of(11, 0), LocalTime.of(15, 0), 1, 1);
    }

    private static boolean assigned(List<RosterAssignment> list, int employeeId, int shiftId) {
        return list.stream().anyMatch(a -> a.getEmployeeId() == employeeId && a.getShiftId() == shiftId);
    }

    @Test
    void cheaperEmployeeGetsTheShift() {
        // Two equally available staff, one shift: before rate-weighted cost this was a coin toss.
        SchedulingEngine.SolveResult r = new SchedulingEngine().solve(
                List.of(emp(1, "10.00", "45.00"), emp(2, "20.00", "45.00")),
                List.of(lunch(1, 1)), alwaysAvailable(1, 2), 10.0);
        assertTrue(r.isFeasible());
        assertEquals(0, r.totalUnfilled);
        assertTrue(assigned(r.assignments, 1, 1), "the RM10 employee should take the shift");
    }

    @Test
    void lowerRateEmployeeTakesTheHolidayShift() {
        // Each can work only one 4-hour shift (cap 4h). Day 2 is a public holiday (paid 2x).
        // Best: RM10 on the holiday (80) + RM12 on the normal day (48) = 128,
        // not RM12 on the holiday (96) + RM10 on the normal day (40) = 136.
        Set<LocalDate> holidays = Set.of(MONDAY.plusDays(1));
        SchedulingEngine.SolveResult r = new SchedulingEngine().solve(
                List.of(emp(1, "10.00", "4.00"), emp(2, "12.00", "4.00")),
                List.of(lunch(1, 1), lunch(2, 2)), alwaysAvailable(1, 2), List.of(),
                MONDAY, holidays, 10.0);
        assertTrue(r.isFeasible());
        assertEquals(0, r.totalUnfilled);
        assertTrue(assigned(r.assignments, 1, 2), "RM10 employee should work the holiday shift");
        assertTrue(assigned(r.assignments, 2, 1), "RM12 employee should work the normal shift");
    }

    @Test
    void withoutHolidayInformationTheHolidayIsNotDoubled() {
        // Same data, no holidays passed: the two layouts cost the same (88), so either is fine,
        // but both shifts must be filled and nobody exceeds the 4h cap.
        SchedulingEngine.SolveResult r = new SchedulingEngine().solve(
                List.of(emp(1, "10.00", "4.00"), emp(2, "12.00", "4.00")),
                List.of(lunch(1, 1), lunch(2, 2)), alwaysAvailable(1, 2), 10.0);
        assertTrue(r.isFeasible());
        assertEquals(0, r.totalUnfilled);
        assertEquals(2, r.assignments.size());
    }

    @Test
    void expensiveHolidayShiftIsStillFilled() {
        // Cost 200 x 4 x 2 = RM1,600, but an unfilled position is priced above that,
        // so coverage still wins (guards the old "solver leaves shifts empty" bug).
        SchedulingEngine.SolveResult r = new SchedulingEngine().solve(
                List.of(emp(1, "200.00", "45.00")),
                List.of(lunch(1, 2)), alwaysAvailable(1), List.of(),
                MONDAY, Set.of(MONDAY.plusDays(1)), 10.0);
        assertTrue(r.isFeasible());
        assertEquals(0, r.totalUnfilled);
        assertEquals(1, r.assignments.size());
    }
}
