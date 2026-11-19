package com.questiu.scheduler;

import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.EmploymentType;
import com.questiu.scheduler.model.PayrollRecord;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;
import com.questiu.scheduler.payroll.PayrollCalculator;
import com.questiu.scheduler.solver.LabourCost;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The solver's labour-cost term must agree with what PayrollCalculator pays for the
 * same shift, otherwise the solver would be minimising a number that is not the payroll.
 * These tests need no OR-Tools native library.
 */
class LabourCostTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    private static Employee emp(String rate) {
        return new Employee(1, "T", 1, new BigDecimal(rate), new BigDecimal("45.00"), true,
                "1234567890", EmploymentType.FULL_TIME);
    }

    private static Shift shift(int day, int startHour, int startMin, int endHour, int endMin) {
        return new Shift(1, day, LocalTime.of(startHour, startMin), LocalTime.of(endHour, endMin), 1, 1);
    }

    /** What PayrollCalculator pays (regular + holiday pay) for one employee on one shift, in sen. */
    private static long payrollSen(Employee e, Shift s, Set<LocalDate> holidays) {
        PayrollRecord r = new PayrollCalculator().calculate(List.of(e), Map.of(s.getShiftId(), s),
                List.of(new RosterAssignment(e.getEmployeeId(), s.getShiftId())), MONDAY, holidays).get(0);
        return r.getRegularPay().add(r.getHolidayPay()).movePointRight(2).longValueExact();
    }

    @Test
    void normalShiftCostEqualsPayrollPay() {
        for (String rate : new String[]{"10.00", "11.50", "12.00", "12.50", "13.50", "15.00"}) {
            Employee e = emp(rate);
            Shift s = shift(1, 11, 0, 15, 0);
            assertEquals(payrollSen(e, s, Set.of()), LabourCost.positionCostSen(e, s, false), "rate " + rate);
        }
    }

    @Test
    void holidayShiftCostIsDoubleAndEqualsPayrollPay() {
        Shift s = shift(2, 18, 0, 22, 0);                       // Tuesday 03/11/2026
        Set<LocalDate> holidays = Set.of(MONDAY.plusDays(1));
        for (String rate : new String[]{"10.00", "11.50", "13.50", "15.00"}) {
            Employee e = emp(rate);
            assertEquals(payrollSen(e, s, holidays), LabourCost.positionCostSen(e, s, true), "rate " + rate);
            assertEquals(2 * LabourCost.positionCostSen(e, s, false), LabourCost.positionCostSen(e, s, true));
        }
    }

    @Test
    void partHourShiftCostMatchesPayroll() {
        Employee e = emp("11.50");
        Shift s = shift(3, 11, 0, 14, 30);                      // 3.5 hours -> RM40.25
        assertEquals(4025, LabourCost.positionCostSen(e, s, false));
        assertEquals(payrollSen(e, s, Set.of()), LabourCost.positionCostSen(e, s, false));
    }

    @Test
    void dearerEmployeeCostsMoreForTheSameShift() {
        Shift s = shift(1, 11, 0, 15, 0);
        assertTrue(LabourCost.positionCostSen(emp("15.00"), s, false) > LabourCost.positionCostSen(emp("12.00"), s, false));
    }

    @Test
    void unfilledPenaltyAlwaysExceedsTheCostOfFillingAPosition() {
        // The dearest realistic case: RM200/h for 12 hours on a holiday.
        long dear = LabourCost.positionCostSen(emp("200.00"), shift(1, 8, 0, 20, 0), true);
        assertEquals(480_000, dear);
        assertTrue(LabourCost.unfilledPenalty(dear) > dear);
        assertEquals(LabourCost.MIN_UNFILLED_PENALTY, LabourCost.unfilledPenalty(9_600),
                "for ordinary shifts the floor of RM10,000 applies");
        assertEquals(4_800_000, LabourCost.unfilledPenalty(dear), "very dear shifts: ten times the cost");
    }
}
