package com.questiu.scheduler;

import com.questiu.scheduler.model.Availability;
import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.EmploymentType;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;
import com.questiu.scheduler.solver.ComplianceChecker;
import com.questiu.scheduler.solver.ComplianceChecker.Rule;
import com.questiu.scheduler.solver.ManualScheduleValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The independent roster audit. No database, JavaFX or OR-Tools needed. */
class ComplianceCheckerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    private static Employee emp(int id, int roleId, String maxHours) {
        return new Employee(id, "Emp" + id, roleId, new BigDecimal("12.00"), new BigDecimal(maxHours), true,
                "1234567890", EmploymentType.FULL_TIME);
    }

    private static Shift shift(int id, int day, int startHour, int endHour, int roleId, int need) {
        return new Shift(id, day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0), roleId, need);
    }

    private static List<Availability> always(int... ids) {
        List<Availability> list = new ArrayList<>();
        for (int id : ids) for (int day = 1; day <= 7; day++)
            list.add(new Availability(id, day, LocalTime.of(8, 0), LocalTime.of(23, 0)));
        return list;
    }

    private static RosterAssignment a(int employeeId, int shiftId) { return new RosterAssignment(employeeId, shiftId); }

    private static long count(ComplianceChecker.Report r, Rule rule) {
        return r.violations.stream().filter(v -> v.rule == rule).count();
    }

    @Test
    void aCleanRosterHasNoViolationsAndFullCoverage() {
        ComplianceChecker.Report r = ComplianceChecker.check(
                List.of(emp(1, 1, "45")), List.of(shift(1, 1, 11, 15, 1, 1), shift(2, 2, 11, 15, 1, 1)),
                always(1), List.of(a(1, 1), a(1, 2)), Map.of(), MONDAY);
        assertEquals(0, r.totalViolations);
        assertEquals(0, r.hardViolations);
        assertTrue(r.isCompliant());
        assertEquals(100.0, r.coveragePercent(), 1e-9);
    }

    @Test
    void roleMismatchIsReported() {
        ComplianceChecker.Report r = ComplianceChecker.check(
                List.of(emp(1, 2, "45")), List.of(shift(1, 1, 11, 15, 1, 1)),
                always(1), List.of(a(1, 1)), Map.of(), MONDAY);
        assertEquals(1, count(r, Rule.ROLE_MATCH));
        assertEquals(1, r.hardViolations);
    }

    @Test
    void anAssignmentOutsideTheAvailabilityWindowIsReported() {
        List<Availability> window = List.of(new Availability(1, 1, LocalTime.of(11, 0), LocalTime.of(13, 0)));
        ComplianceChecker.Report r = ComplianceChecker.check(
                List.of(emp(1, 1, "45")), List.of(shift(1, 1, 11, 15, 1, 1)),   // shift runs past the window
                window, List.of(a(1, 1)), Map.of(), MONDAY);
        assertEquals(1, count(r, Rule.AVAILABILITY));
        // no availability rows at all is also a violation
        ComplianceChecker.Report none = ComplianceChecker.check(
                List.of(emp(1, 1, "45")), List.of(shift(1, 1, 11, 15, 1, 1)),
                List.of(), List.of(a(1, 1)), Map.of(), MONDAY);
        assertEquals(1, count(none, Rule.AVAILABILITY));
    }

    @Test
    void leaveIsReportedOnceAndNotAlsoAsUnavailable() {
        ComplianceChecker.Report r = ComplianceChecker.check(
                List.of(emp(1, 1, "45")), List.of(shift(1, 3, 11, 15, 1, 1)),   // Wednesday
                always(1), List.of(a(1, 1)), Map.of(1, Set.of(3)), MONDAY);
        assertEquals(1, count(r, Rule.LEAVE));
        assertEquals(0, count(r, Rule.AVAILABILITY));
        assertTrue(r.violations.get(0).description.contains("04/11/2026"), "the calendar date is printed");

        // The realistic case: leave days have been removed from the person's availability, so
        // there is no Wednesday window at all. It must still be ONE leave violation, not two.
        List<Availability> noWednesday = new ArrayList<>(always(1));
        noWednesday.removeIf(w -> w.getDayOfWeek() == 3);
        ComplianceChecker.Report filtered = ComplianceChecker.check(
                List.of(emp(1, 1, "45")), List.of(shift(1, 3, 11, 15, 1, 1)),
                noWednesday, List.of(a(1, 1)), Map.of(1, Set.of(3)), MONDAY);
        assertEquals(1, count(filtered, Rule.LEAVE));
        assertEquals(0, count(filtered, Rule.AVAILABILITY));
        assertEquals(1, filtered.hardViolations);
    }

    @Test
    void overlapAndDoubleBookingAreReported() {
        ComplianceChecker.Report overlap = ComplianceChecker.check(
                List.of(emp(1, 1, "45")), List.of(shift(1, 1, 11, 15, 1, 1), shift(2, 1, 14, 18, 1, 1)),
                always(1), List.of(a(1, 1), a(1, 2)), Map.of(), MONDAY);
        assertEquals(1, count(overlap, Rule.NO_OVERLAP));
        // the same person twice on the same shift is also double-booked (and over-staffs it)
        ComplianceChecker.Report twice = ComplianceChecker.check(
                List.of(emp(1, 1, "45")), List.of(shift(1, 1, 11, 15, 1, 2)),
                always(1), List.of(a(1, 1), a(1, 1)), Map.of(), MONDAY);
        assertEquals(1, count(twice, Rule.NO_OVERLAP));
    }

    @Test
    void weeklyHoursAboveTheCapAreReportedWithTheExcessAsSeverity() {
        List<Shift> shifts = List.of(shift(1, 1, 8, 16, 1, 1), shift(2, 2, 8, 16, 1, 1));  // 16 h
        ComplianceChecker.Report r = ComplianceChecker.check(
                List.of(emp(1, 1, "12")), shifts, always(1), List.of(a(1, 1), a(1, 2)), Map.of(), MONDAY);
        assertEquals(1, count(r, Rule.MAX_HOURS));
        assertEquals(4.0, r.violations.stream().filter(v -> v.rule == Rule.MAX_HOURS).findFirst().get().severity, 1e-9);
    }

    @Test
    void tooManyPeopleIsAHardViolationAndTooFewIsASoftShortfall() {
        List<Employee> staff = List.of(emp(1, 1, "45"), emp(2, 1, "45"), emp(3, 1, "45"));
        List<Shift> shifts = List.of(shift(1, 1, 11, 15, 1, 1), shift(2, 2, 11, 15, 1, 2));
        // shift 1 needs 1 but gets 2 (over); shift 2 needs 2 but gets 1 (short by 1)
        ComplianceChecker.Report r = ComplianceChecker.check(staff, shifts, always(1, 2, 3),
                List.of(a(1, 1), a(2, 1), a(3, 2)), Map.of(), MONDAY);
        assertEquals(1, count(r, Rule.COVERAGE));
        assertEquals(1, count(r, Rule.UNFILLED));
        assertEquals(1, r.hardViolations, "a shortfall is not a hard violation");
        assertEquals(2, r.totalViolations);
        assertEquals(1, r.unfilledPositions);
        assertEquals(3, r.totalPositions);
        assertEquals(100.0 * 2 / 3, r.coveragePercent(), 1e-9);
    }

    @Test
    void anEmptyRosterIsAllShortfallAndNoHardViolations() {
        ComplianceChecker.Report r = ComplianceChecker.check(
                List.of(emp(1, 1, "45")), List.of(shift(1, 1, 11, 15, 1, 2)),
                always(1), List.of(), Map.of(), MONDAY);
        assertTrue(r.isCompliant());
        assertEquals(0.0, r.coveragePercent(), 1e-9);
        assertEquals(2, r.unfilledPositions);
    }

    @Test
    void unknownEmployeesOrShiftsAreIgnoredAndDescriptionsStayShort() {
        ComplianceChecker.Report r = ComplianceChecker.check(
                List.of(emp(1, 1, "45")), List.of(shift(1, 1, 11, 15, 1, 1)),
                always(1), List.of(a(99, 1), a(1, 99)), Map.of(), MONDAY);
        assertEquals(0, r.hardViolations);
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 300; i++) longName.append('x');
        Employee longNamed = new Employee(1, longName.toString(), 2, new BigDecimal("12"), new BigDecimal("45"),
                true, "1", EmploymentType.FULL_TIME);
        ComplianceChecker.Report r2 = ComplianceChecker.check(
                List.of(longNamed), List.of(shift(1, 1, 11, 15, 1, 1)),
                always(1), List.of(a(1, 1)), Map.of(), MONDAY);
        assertFalse(r2.violations.isEmpty());
        assertTrue(r2.violations.stream().allMatch(v -> v.description.length() <= 255),
                "constraint_violation.description is VARCHAR(255)");
    }

    @Test
    void agreesWithTheManualValidatorOnOverlapHoursAndLeave() {
        List<Employee> staff = List.of(emp(1, 1, "10"), emp(2, 1, "45"));
        List<Shift> shifts = List.of(shift(1, 1, 11, 15, 1, 1), shift(2, 1, 14, 18, 1, 1),
                shift(3, 2, 8, 16, 1, 1), shift(4, 3, 11, 15, 1, 1));
        // emp 1: overlap (shifts 1+2) and 4+4+8 = 16 h > 10; emp 2 is on leave Wednesday (day 3) but is on shift 4
        List<RosterAssignment> roster = List.of(a(1, 1), a(1, 2), a(1, 3), a(2, 4));
        Map<Integer, Set<Integer>> leave = Map.of(2, Set.of(3));
        ComplianceChecker.Report mine = ComplianceChecker.check(staff, shifts, always(1, 2), roster, leave, MONDAY);
        ManualScheduleValidator.ValidationResult theirs =
                new ManualScheduleValidator().validate(staff, shifts, roster, leave, MONDAY);
        long overlapHoursLeave = count(mine, Rule.NO_OVERLAP) + count(mine, Rule.MAX_HOURS) + count(mine, Rule.LEAVE);
        assertEquals(theirs.violations.size(), overlapHoursLeave);
        assertEquals(theirs.unfilledSlots, mine.unfilledPositions);
        assertEquals(theirs.coveragePercent(), mine.coveragePercent(), 1e-9);
    }

    @Test
    void workingAllSevenDaysBreaksTheRestDayRuleButSixDaysIsFine() {
        List<Shift> week = new ArrayList<>();
        List<RosterAssignment> sevenDays = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            week.add(shift(day, day, 11, 15, 1, 1));
            sevenDays.add(a(1, day));
        }
        ComplianceChecker.Report seven = ComplianceChecker.check(
                List.of(emp(1, 1, "45")), week, always(1), sevenDays, Map.of(), MONDAY);
        assertEquals(1, count(seven, Rule.REST_DAY));
        assertEquals(1, seven.hardViolations);
        assertEquals(1.0, seven.violations.get(0).severity, 1e-9);

        ComplianceChecker.Report six = ComplianceChecker.check(
                List.of(emp(1, 1, "45")), week, always(1), sevenDays.subList(0, 6), Map.of(), MONDAY);
        assertEquals(0, count(six, Rule.REST_DAY));
        // two shifts on the same day count as one working day
        List<Shift> doubleDay = List.of(shift(1, 1, 8, 11, 1, 1), shift(2, 1, 12, 15, 1, 1));
        ComplianceChecker.Report oneDay = ComplianceChecker.check(
                List.of(emp(1, 1, "45")), doubleDay, always(1), List.of(a(1, 1), a(1, 2)), Map.of(), MONDAY);
        assertEquals(0, count(oneDay, Rule.REST_DAY));
    }
}
