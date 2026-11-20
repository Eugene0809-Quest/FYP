package com.questiu.scheduler;

import com.questiu.scheduler.model.Availability;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.EmploymentType;
import com.questiu.scheduler.model.Preference;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;
import com.questiu.scheduler.solver.SchedulingEngine;
import com.questiu.scheduler.solver.SolverWeights;
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

    @Test
    void objectiveValueIsTheTotalCostInRinggit() {
        // One RM10 employee on one 4-hour shift: cost RM40.00, nobody to balance against,
        // no preferences, nothing unfilled, so Z = RM40.00 exactly.
        SchedulingEngine.SolveResult r = new SchedulingEngine().solve(
                List.of(emp(1, "10.00", "45.00")), List.of(lunch(1, 1)), alwaysAvailable(1), 10.0);
        assertTrue(r.isFeasible());
        assertEquals(40.00, r.objectiveValueRm(), 0.001);
    }

    @Test
    void managerCanTradeCostForPreferenceThroughTheWeights() {
        // RM10 employee 1 and RM12 employee 2 for one 4-hour shift. Employee 2 strongly prefers it.
        // Default (RM2 per level): preference is worth RM4 but employee 2 costs RM8 more -> employee 1.
        // Preference valued at RM10 per level: worth RM20 > RM8 -> employee 2.
        List<Employee> staff = List.of(emp(1, "10.00", "45.00"), emp(2, "12.00", "45.00"));
        List<Preference> prefs = List.of(new Preference(2, 1, 2));

        SchedulingEngine.SolveResult byDefault = new SchedulingEngine().solve(
                staff, List.of(lunch(1, 1)), alwaysAvailable(1, 2), prefs, null, Set.of(),
                SolverWeights.defaults(), 10.0);
        assertTrue(assigned(byDefault.assignments, 1, 1), "default weights: cost beats a RM4 preference");

        SolverWeights preferenceFirst = new SolverWeights(10_000, 5, 10.0, 100, 1.0);
        SchedulingEngine.SolveResult byPreference = new SchedulingEngine().solve(
                staff, List.of(lunch(1, 1)), alwaysAvailable(1, 2), prefs, null, Set.of(),
                preferenceFirst, 10.0);
        assertTrue(assigned(byPreference.assignments, 2, 1), "RM10 per level: the preference wins");
    }

    @Test
    void aTooSmallUnfilledPriceStillCannotEmptyAShift() {
        // Unfilled price set to RM1 (absurdly low): the solver must still staff the RM200/h holiday shift.
        SolverWeights tiny = new SolverWeights(1.0, 5, 2.0, 100, 1.0);
        SchedulingEngine.SolveResult r = new SchedulingEngine().solve(
                List.of(emp(1, "200.00", "45.00")), List.of(lunch(1, 2)), alwaysAvailable(1), List.of(),
                MONDAY, Set.of(MONDAY.plusDays(1)), tiny, 10.0);
        assertTrue(r.isFeasible());
        assertEquals(0, r.totalUnfilled);
    }

    @Test
    void staffWithNoAvailabilityDoNotDistortTheFairnessComparison() {
        // Employees 1 and 2 cost RM10/h, employee 3 costs RM15/h, employee 4 (RM10/h) has no
        // availability at all. Six 4-hour shifts need one person each.
        // If employee 4 counted as "0 hours" the cheapest roster would give the RM15 employee
        // nothing (3, 3, 0 shifts). Left out of the comparison, the fair split (2, 2, 2) is best.
        List<Employee> staff = List.of(emp(1, "10.00", "45.00"), emp(2, "10.00", "45.00"),
                emp(3, "15.00", "45.00"), emp(4, "10.00", "45.00"));
        List<Shift> shifts = List.of(lunch(1, 1), lunch(2, 2), lunch(3, 3), lunch(4, 4), lunch(5, 5), lunch(6, 6));
        SchedulingEngine.SolveResult r = new SchedulingEngine().solve(
                staff, shifts, alwaysAvailable(1, 2, 3), 10.0);
        assertTrue(r.isFeasible());
        assertEquals(0, r.totalUnfilled);
        assertEquals(2, r.assignments.stream().filter(a -> a.getEmployeeId() == 1).count());
        assertEquals(2, r.assignments.stream().filter(a -> a.getEmployeeId() == 2).count());
        assertEquals(2, r.assignments.stream().filter(a -> a.getEmployeeId() == 3).count());
        assertEquals(0, r.assignments.stream().filter(a -> a.getEmployeeId() == 4).count());
    }
}
