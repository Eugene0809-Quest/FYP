package com.questiu.scheduler;

import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.PayrollRecord;
import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;
import com.questiu.scheduler.payroll.PayrollCalculator;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Manual (no JUnit) validation of PayrollCalculator against Table 3.1 of the
 * FYP1 report: Rate=RM12.00, Hours=48, H=45 -> expected TotalPay = RM594.00.
 * Run directly with: java -cp <classes> com.questiu.scheduler.PayrollCalculatorManualTest
 */
public class PayrollCalculatorManualTest {
    public static void main(String[] args) {
        // One employee, max 45h/week, rate RM12.00/hour
        Employee emp = new Employee(1, "TestEmployee", 1,
                new BigDecimal("12.00"), new BigDecimal("45.00"), true);

        // Build shifts that sum to 48 hours total (matches Table 3.1: Hours_e = 48)
        // 6 shifts of 8 hours each = 48 hours
        Map<Integer, Shift> shiftsById = new java.util.HashMap<>();
        List<RosterAssignment> assignments = new java.util.ArrayList<>();
        for (int day = 1; day <= 6; day++) {
            Shift s = new Shift(day, 1, day, LocalTime.of(9, 0), LocalTime.of(17, 0), 1, 1);
            shiftsById.put(day, s);
            assignments.add(new RosterAssignment(1, day));
        }

        PayrollCalculator calc = new PayrollCalculator();
        List<PayrollRecord> results = calc.calculate(List.of(emp), shiftsById, assignments);
        PayrollRecord record = results.get(0);

        System.out.println("=== PayrollCalculator validation against Report Table 3.1 ===");
        System.out.println(record);
        System.out.println();
        System.out.printf("Expected: hours=48.0, regular=45.0, OT=3.0, regularPay=540.00, otPay=54.00, totalPay=594.00%n");

        boolean pass = record.getTotalHours() == 48.0
                && record.getRegularHours() == 45.0
                && record.getOvertimeHours() == 3.0
                && record.getRegularPay().compareTo(new BigDecimal("540.00")) == 0
                && record.getOvertimePay().compareTo(new BigDecimal("54.00")) == 0
                && record.getTotalPay().compareTo(new BigDecimal("594.00")) == 0;

        System.out.println(pass ? "RESULT: PASS - matches report Table 3.1 exactly" : "RESULT: FAIL - mismatch, check formula");
        if (!pass) System.exit(1);
    }
}
