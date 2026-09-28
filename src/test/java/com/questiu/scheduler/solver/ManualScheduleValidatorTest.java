package com.questiu.scheduler.solver;

import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.EmploymentType;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for item #3's manual-mode compliance checker. ManualAssignmentPane's
 * eligible-employee filtering already prevents ROLE_MATCH and AVAILABILITY
 * violations by construction (see its class javadoc), so this only needs to
 * cover the two constraints that CAN'T be filtered per-slot - NO_OVERLAP and
 * MAX_HOURS - plus the coverage/unfilled-slot bookkeeping.
 *
 * Run with: mvn test
 */
class ManualScheduleValidatorTest {

    private Employee employee(BigDecimal maxHours) {
        return new Employee(1, "Test Employee", 1, new BigDecimal("12.00"), maxHours, true,
                "1234567890", EmploymentType.FULL_TIME);
    }

    private Shift shift(int id, int dayOfWeek, int startHour, int endHour) {
        return new Shift(id, dayOfWeek, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0), 1, 1);
    }

    @Test
    void cleanScheduleHasNoViolationsAndFullCoverage() {
        Employee e = employee(new BigDecimal("45.00"));
        Shift s1 = shift(1, 1, 9, 13);  // Monday 9-13 (4h)
        Shift s2 = shift(2, 2, 9, 13);  // Tuesday 9-13 (4h) - different day, no overlap
        List<RosterAssignment> assignments = List.of(
                new RosterAssignment(1, 1),
                new RosterAssignment(1, 2)
        );

        ManualScheduleValidator.ValidationResult result =
                new ManualScheduleValidator().validate(List.of(e), List.of(s1, s2), assignments);

        assertTrue(result.isClean(), "Expected no violations, got: " + result.violations);
        assertEquals(0, result.unfilledSlots);
        assertEquals(2, result.totalSlots);
        assertEquals(100.0, result.coveragePercent(), 0.001);
    }

    @Test
    void detectsOverlappingShiftsForSameEmployee() {
        Employee e = employee(new BigDecimal("45.00"));
        Shift s1 = shift(1, 1, 9, 13);   // Monday 9-13
        Shift s2 = shift(2, 1, 11, 15);  // Monday 11-15 - overlaps s1
        List<RosterAssignment> assignments = List.of(
                new RosterAssignment(1, 1),
                new RosterAssignment(1, 2)
        );

        ManualScheduleValidator.ValidationResult result =
                new ManualScheduleValidator().validate(List.of(e), List.of(s1, s2), assignments);

        assertFalse(result.isClean());
        assertTrue(result.violations.stream().anyMatch(v -> v.contains("double-booked")),
                "Expected an overlap violation, got: " + result.violations);
    }

    @Test
    void detectsExceedingMaxWeeklyHours() {
        // max_hours_week = 10, but assigned two 8-hour shifts on different days = 16h total
        Employee e = employee(new BigDecimal("10.00"));
        Shift s1 = shift(1, 1, 9, 17);  // Monday 9-17 (8h)
        Shift s2 = shift(2, 2, 9, 17);  // Tuesday 9-17 (8h)
        List<RosterAssignment> assignments = List.of(
                new RosterAssignment(1, 1),
                new RosterAssignment(1, 2)
        );

        ManualScheduleValidator.ValidationResult result =
                new ManualScheduleValidator().validate(List.of(e), List.of(s1, s2), assignments);

        assertFalse(result.isClean());
        assertTrue(result.violations.stream().anyMatch(v -> v.contains("exceeding")),
                "Expected a max-hours violation, got: " + result.violations);
    }

    @Test
    void unfilledSlotsAndCoverageAreCountedCorrectly() {
        Employee e = employee(new BigDecimal("45.00"));
        Shift s1 = shift(1, 1, 9, 13);
        Shift s2 = shift(2, 2, 9, 13); // left unassigned
        List<RosterAssignment> assignments = List.of(new RosterAssignment(1, 1));

        ManualScheduleValidator.ValidationResult result =
                new ManualScheduleValidator().validate(List.of(e), List.of(s1, s2), assignments);

        assertEquals(2, result.totalSlots);
        assertEquals(1, result.unfilledSlots);
        assertEquals(50.0, result.coveragePercent(), 0.001);
    }
}
