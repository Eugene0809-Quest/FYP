package com.questiu.scheduler.payroll;

import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.EmploymentType;
import com.questiu.scheduler.model.PayrollRecord;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for item #4 (public holiday 2x pay) and item #5 (EPF/SOCSO/EIS)
 * added to PayrollCalculator. The original Section 3.5 regular/overtime
 * formula is already covered by PayrollCalculatorManualTest (run manually,
 * matches report Table 3.1) - regularOvertimeFormulaStillMatchesTable3_1
 * below re-checks the same figures through the new 5-arg signature so both
 * old and new code paths are verified in one `mvn test` run.
 *
 * Expected figures were hand-computed with exact decimal arithmetic (not
 * floating point) before writing these assertions, using the same rounding
 * (HALF_UP, 2dp) PayrollCalculator itself uses - see StatutoryRates for the
 * underlying percentages and thresholds.
 *
 * Run with: mvn test
 */
class PayrollCalculatorTest {

    private final PayrollCalculator calc = new PayrollCalculator();
    private static final LocalDate WEEK_START = LocalDate.of(2026, 9, 21); // a Monday

    private Employee employee(BigDecimal hourlyRate, BigDecimal maxWeeklyHours) {
        return new Employee(1, "Test Employee", 1, hourlyRate, maxWeeklyHours, true,
                "1234567890", EmploymentType.FULL_TIME);
    }

    private PayrollRecord solve(Employee emp, List<Shift> shifts, Set<LocalDate> holidays) {
        Map<Integer, Shift> shiftsById = shifts.stream().collect(Collectors.toMap(Shift::getShiftId, s -> s));
        List<RosterAssignment> assignments = shifts.stream()
                .map(s -> new RosterAssignment(emp.getEmployeeId(), s.getShiftId()))
                .collect(Collectors.toList());
        return calc.calculate(List.of(emp), shiftsById, assignments, WEEK_START, holidays).get(0);
    }

    // ---- Item #4: public holiday pay ----

    @Test
    void holidayHoursArePaidDoubleAndExcludedFromRegularPool() {
        Employee emp = employee(new BigDecimal("12.00"), new BigDecimal("45.00"));
        // dayOfWeek=1 falls exactly on WEEK_START (a Monday) - make that date a holiday
        Shift holidayShift = new Shift(1, 1, LocalTime.of(9, 0), LocalTime.of(13, 0), 1, 1); // 4h
        Set<LocalDate> holidays = Set.of(WEEK_START);

        PayrollRecord record = solve(emp, List.of(holidayShift), holidays);

        assertEquals(4.0, record.getHolidayHours(), 0.001);
        assertEquals(0.0, record.getRegularHours(), 0.001);
        assertEquals(0.0, record.getOvertimeHours(), 0.001);
        assertEquals(new BigDecimal("96.00"), record.getHolidayPay());  // 12.00 * 2.0 * 4h
        assertEquals(new BigDecimal("96.00"), record.getTotalPay());
        // EPF must be computed on the holiday-inclusive wage base: 96.00 * 0.11 = 10.56
        assertEquals(new BigDecimal("10.56"), record.getStatutory().getEmployeeEpf());
    }

    @Test
    void nonHolidayShiftIsUnaffectedByAnUnrelatedHolidayCalendar() {
        Employee emp = employee(new BigDecimal("12.00"), new BigDecimal("45.00"));
        Shift normalShift = new Shift(1, 1, LocalTime.of(9, 0), LocalTime.of(13, 0), 1, 1); // 4h
        Set<LocalDate> holidays = Set.of(LocalDate.of(2099, 1, 1)); // unrelated date

        PayrollRecord record = solve(emp, List.of(normalShift), holidays);

        assertEquals(0.0, record.getHolidayHours(), 0.001);
        assertEquals(4.0, record.getRegularHours(), 0.001);
        assertEquals(new BigDecimal("0.00"), record.getHolidayPay());
        assertEquals(new BigDecimal("48.00"), record.getTotalPay()); // 12.00 * 4h
    }

    /** Same case PayrollCalculatorManualTest checks manually - re-verified here through the new 5-arg signature. */
    @Test
    void regularOvertimeFormulaStillMatchesReportTable3_1() {
        Employee emp = employee(new BigDecimal("12.00"), new BigDecimal("45.00"));
        // 6 shifts of 8h each = 48h total, on 6 different days
        List<Shift> shifts = List.of(
                new Shift(1, 1, LocalTime.of(9, 0), LocalTime.of(17, 0), 1, 1),
                new Shift(2, 2, LocalTime.of(9, 0), LocalTime.of(17, 0), 1, 1),
                new Shift(3, 3, LocalTime.of(9, 0), LocalTime.of(17, 0), 1, 1),
                new Shift(4, 4, LocalTime.of(9, 0), LocalTime.of(17, 0), 1, 1),
                new Shift(5, 5, LocalTime.of(9, 0), LocalTime.of(17, 0), 1, 1),
                new Shift(6, 6, LocalTime.of(9, 0), LocalTime.of(17, 0), 1, 1)
        );

        PayrollRecord record = solve(emp, shifts, Set.of());

        assertEquals(48.0, record.getTotalHours(), 0.001);
        assertEquals(45.0, record.getRegularHours(), 0.001);
        assertEquals(3.0, record.getOvertimeHours(), 0.001);
        assertEquals(new BigDecimal("540.00"), record.getRegularPay());
        assertEquals(new BigDecimal("54.00"), record.getOvertimePay());
        assertEquals(new BigDecimal("594.00"), record.getTotalPay());
    }

    // ---- Item #5: EPF/SOCSO/EIS ----

    @Test
    void statutoryContributions_belowEpfTierThreshold_belowSocsoCeiling() {
        // RM50/hr x 20h = RM1000 wage base -> estimated monthly ~RM4,333 (<=RM5,000) -> 13% employer EPF
        // RM1000 is also well under the RM1,384.62/week SOCSO/EIS ceiling, so those are uncapped.
        Employee emp = employee(new BigDecimal("50.00"), new BigDecimal("45.00"));
        Shift s = new Shift(1, 1, LocalTime.of(0, 0), LocalTime.of(20, 0), 1, 1); // 20h

        PayrollRecord record = solve(emp, List.of(s), Set.of());
        PayrollRecord.StatutoryBreakdown st = record.getStatutory();

        assertEquals(new BigDecimal("1000.00"), record.getTotalPay());
        assertEquals(new BigDecimal("110.00"), st.getEmployeeEpf());   // 1000 * 0.11
        assertEquals(new BigDecimal("130.00"), st.getEmployerEpf());   // 1000 * 0.13
        assertEquals(new BigDecimal("5.00"), st.getEmployeeSocso());   // 1000 * 0.005
        assertEquals(new BigDecimal("17.50"), st.getEmployerSocso());  // 1000 * 0.0175
        assertEquals(new BigDecimal("2.00"), st.getEmployeeEis());     // 1000 * 0.002
        assertEquals(new BigDecimal("2.00"), st.getEmployerEis());     // 1000 * 0.002
        assertEquals(new BigDecimal("883.00"), st.getNetPay());        // 1000 - 110 - 5 - 2
        assertEquals(new BigDecimal("1149.50"), st.getEmployerTotalCost()); // 1000 + 130 + 17.50 + 2
    }

    @Test
    void statutoryContributions_aboveEpfTierThreshold_switchesEmployerRateTo12Percent() {
        // RM50/hr x 26h (across 2 days, since a single day can't hold 26h) = RM1300 wage base
        // -> estimated monthly ~RM5,633 (> RM5,000) -> employer EPF drops to 12%, employee stays 11%
        Employee emp = employee(new BigDecimal("50.00"), new BigDecimal("45.00"));
        List<Shift> shifts = List.of(
                new Shift(1, 1, LocalTime.of(0, 0), LocalTime.of(20, 0), 1, 1), // 20h
                new Shift(2, 2, LocalTime.of(0, 0), LocalTime.of(6, 0), 1, 1)   // 6h
        );

        PayrollRecord record = solve(emp, shifts, Set.of());
        PayrollRecord.StatutoryBreakdown st = record.getStatutory();

        assertEquals(new BigDecimal("1300.00"), record.getTotalPay());
        assertEquals(new BigDecimal("143.00"), st.getEmployeeEpf());  // 1300 * 0.11 - unchanged
        assertEquals(new BigDecimal("156.00"), st.getEmployerEpf());  // 1300 * 0.12 - dropped from 13%
    }

    @Test
    void statutoryContributions_aboveSocsoEisCeiling_capsAtRM6000Monthly() {
        // RM50/hr x 30h (across 2 days) = RM1500 wage base -> above the RM1,384.62/week SOCSO/EIS
        // ceiling, so SOCSO/EIS are charged on the CAPPED base, while EPF (no ceiling) still uses
        // the full RM1500.
        Employee emp = employee(new BigDecimal("50.00"), new BigDecimal("45.00"));
        List<Shift> shifts = List.of(
                new Shift(1, 1, LocalTime.of(0, 0), LocalTime.of(20, 0), 1, 1), // 20h
                new Shift(2, 2, LocalTime.of(0, 0), LocalTime.of(10, 0), 1, 1)  // 10h
        );

        PayrollRecord record = solve(emp, shifts, Set.of());
        PayrollRecord.StatutoryBreakdown st = record.getStatutory();

        assertEquals(new BigDecimal("1500.00"), record.getTotalPay());
        assertEquals(new BigDecimal("165.00"), st.getEmployeeEpf());   // 1500 * 0.11 - EPF has no ceiling
        assertEquals(new BigDecimal("180.00"), st.getEmployerEpf());   // 1500 * 0.12
        assertEquals(new BigDecimal("6.92"), st.getEmployeeSocso());   // 1384.62 (capped) * 0.005
        assertEquals(new BigDecimal("24.23"), st.getEmployerSocso());  // 1384.62 (capped) * 0.0175
        assertEquals(new BigDecimal("2.77"), st.getEmployeeEis());     // 1384.62 (capped) * 0.002
        assertEquals(new BigDecimal("2.77"), st.getEmployerEis());
        assertEquals(new BigDecimal("1325.31"), st.getNetPay());
        assertEquals(new BigDecimal("1707.00"), st.getEmployerTotalCost());
    }
}
