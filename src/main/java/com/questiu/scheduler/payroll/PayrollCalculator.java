package com.questiu.scheduler.payroll;

import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.PayrollRecord;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implements the payroll formula from Section 3.5:
 *   RegularPay_e  = Rate_e * min(Hours_e, H_e)
 *   OvertimePay_e = Rate_e * 1.5 * max(0, Hours_e - H_e)
 *   TotalPay_e    = RegularPay_e + OvertimePay_e
 *
 * Reads only from the already-solved roster (List<RosterAssignment>) - this is
 * what guarantees the payroll figure can never drift out of sync with the
 * schedule that produced it (Section 2.2.3 / 3.5).
 */
public class PayrollCalculator {

    private static final BigDecimal OVERTIME_MULTIPLIER = new BigDecimal("1.5");

    public List<PayrollRecord> calculate(List<Employee> employees,
                                          Map<Integer, Shift> shiftsById,
                                          List<RosterAssignment> assignments) {

        // Group assignments by employee
        Map<Integer, List<RosterAssignment>> byEmployee = assignments.stream()
                .collect(Collectors.groupingBy(RosterAssignment::getEmployeeId));

        return employees.stream()
                .map(emp -> calculateForEmployee(emp, shiftsById, byEmployee.getOrDefault(emp.getEmployeeId(), List.of())))
                .collect(Collectors.toList());
    }

    private PayrollRecord calculateForEmployee(Employee emp, Map<Integer, Shift> shiftsById,
                                                List<RosterAssignment> empAssignments) {
        double totalHours = empAssignments.stream()
                .mapToDouble(a -> shiftsById.get(a.getShiftId()).durationHours())
                .sum();

        double maxHours = emp.getMaxWeeklyHours().doubleValue();
        double regularHours = Math.min(totalHours, maxHours);
        double overtimeHours = Math.max(0.0, totalHours - maxHours);

        BigDecimal rate = emp.getHourlyRate();
        BigDecimal regularPay = rate.multiply(BigDecimal.valueOf(regularHours))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal overtimePay = rate.multiply(OVERTIME_MULTIPLIER).multiply(BigDecimal.valueOf(overtimeHours))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPay = regularPay.add(overtimePay);

        return new PayrollRecord(emp.getEmployeeId(), emp.getFullName(), totalHours,
                regularHours, overtimeHours, regularPay, overtimePay, totalPay);
    }
}
