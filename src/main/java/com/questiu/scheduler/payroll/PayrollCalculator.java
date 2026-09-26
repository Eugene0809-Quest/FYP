package com.questiu.scheduler.payroll;

import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.PayrollRecord;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements the payroll formula from Section 3.5, now extended for item #4
 * (public holiday premium pay):
 *   RegularPay_e       = Rate_e * min(NormalHours_e, H_e)
 *   OvertimePay_e      = Rate_e * 1.5 * max(0, NormalHours_e - H_e)
 *   PublicHolidayPay_e = Rate_e * 2.0 * HolidayHours_e
 *   TotalPay_e         = RegularPay_e + OvertimePay_e + PublicHolidayPay_e
 *
 * SIMPLIFICATION (flag for the report's scope section, same style as the
 * existing "Known simplifications" list): hours worked on a gazetted public
 * holiday are paid at a flat 2x rate and are excluded from the 45-hour
 * regular/overtime pool entirely, rather than being blended into the normal
 * weekly hour count. This mirrors how public holiday work is a separate
 * entitlement under the Employment Act 1955 rather than ordinary overtime,
 * but does NOT implement every nuance of the Act (e.g. the different
 * treatment for monthly-rated vs. daily-rated employees).
 *
 * Reads only from the already-solved/assigned roster - this is what
 * guarantees the payroll figure can never drift out of sync with the
 * schedule that produced it (Section 2.2.3 / 3.5).
 */
public class PayrollCalculator {

    private static final BigDecimal OVERTIME_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal PUBLIC_HOLIDAY_MULTIPLIER = new BigDecimal("2.0");

    /** Backward-compatible overload (used by PayrollCalculatorManualTest) - no public holidays applied. */
    public List<PayrollRecord> calculate(List<Employee> employees,
                                          Map<Integer, Shift> shiftsById,
                                          List<RosterAssignment> assignments) {
        return calculate(employees, shiftsById, assignments, LocalDate.now(), Set.of());
    }

    /**
     * @param weekStartDate the Monday that shift_definition's day_of_week (1=Mon..7=Sun)
     *                      is measured from for THIS roster run, so each shift's actual
     *                      calendar date can be checked against publicHolidays
     * @param publicHolidays the set of gazetted public holiday dates to pay at 2x
     */
    public List<PayrollRecord> calculate(List<Employee> employees,
                                          Map<Integer, Shift> shiftsById,
                                          List<RosterAssignment> assignments,
                                          LocalDate weekStartDate,
                                          Set<LocalDate> publicHolidays) {

        Map<Integer, List<RosterAssignment>> byEmployee = assignments.stream()
                .collect(Collectors.groupingBy(RosterAssignment::getEmployeeId));

        return employees.stream()
                .map(emp -> calculateForEmployee(emp, shiftsById,
                        byEmployee.getOrDefault(emp.getEmployeeId(), List.of()),
                        weekStartDate, publicHolidays))
                .collect(Collectors.toList());
    }

    private PayrollRecord calculateForEmployee(Employee emp, Map<Integer, Shift> shiftsById,
                                                List<RosterAssignment> empAssignments,
                                                LocalDate weekStartDate, Set<LocalDate> publicHolidays) {
        double normalHours = 0.0;
        double holidayHours = 0.0;
        for (RosterAssignment a : empAssignments) {
            Shift shift = shiftsById.get(a.getShiftId());
            double hours = shift.durationHours();
            if (publicHolidays.contains(shift.actualDate(weekStartDate))) {
                holidayHours += hours;
            } else {
                normalHours += hours;
            }
        }

        double maxHours = emp.getMaxWeeklyHours().doubleValue();
        double regularHours = Math.min(normalHours, maxHours);
        double overtimeHours = Math.max(0.0, normalHours - maxHours);
        double totalHours = normalHours + holidayHours;

        BigDecimal rate = emp.getHourlyRate();
        BigDecimal regularPay = rate.multiply(BigDecimal.valueOf(regularHours))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal overtimePay = rate.multiply(OVERTIME_MULTIPLIER).multiply(BigDecimal.valueOf(overtimeHours))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal holidayPay = rate.multiply(PUBLIC_HOLIDAY_MULTIPLIER).multiply(BigDecimal.valueOf(holidayHours))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPay = regularPay.add(overtimePay).add(holidayPay);

        return new PayrollRecord(emp.getEmployeeId(), emp.getFullName(), totalHours,
                regularHours, overtimeHours, holidayHours, regularPay, overtimePay, holidayPay, totalPay);
    }
}
