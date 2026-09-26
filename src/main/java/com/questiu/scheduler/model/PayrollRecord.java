package com.questiu.scheduler.model;

import java.math.BigDecimal;

/**
 * One employee's payroll result for a week plan (Section 3.5, extended for
 * item #4's public holiday premium). Produced by PayrollCalculator directly
 * from the solved/assigned roster - never entered or edited manually, which
 * is what keeps roster and payroll from drifting apart (Section 2.2.3).
 */
public class PayrollRecord {
    private final int employeeId;
    private final String employeeName;
    private final double totalHours;
    private final double regularHours;
    private final double overtimeHours;
    private final double holidayHours;
    private final BigDecimal regularPay;
    private final BigDecimal overtimePay;
    private final BigDecimal holidayPay;
    private final BigDecimal totalPay;

    public PayrollRecord(int employeeId, String employeeName, double totalHours,
                          double regularHours, double overtimeHours, double holidayHours,
                          BigDecimal regularPay, BigDecimal overtimePay, BigDecimal holidayPay,
                          BigDecimal totalPay) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.totalHours = totalHours;
        this.regularHours = regularHours;
        this.overtimeHours = overtimeHours;
        this.holidayHours = holidayHours;
        this.regularPay = regularPay;
        this.overtimePay = overtimePay;
        this.holidayPay = holidayPay;
        this.totalPay = totalPay;
    }

    public int getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public double getTotalHours() { return totalHours; }
    public double getRegularHours() { return regularHours; }
    public double getOvertimeHours() { return overtimeHours; }
    public double getHolidayHours() { return holidayHours; }
    public BigDecimal getRegularPay() { return regularPay; }
    public BigDecimal getOvertimePay() { return overtimePay; }
    public BigDecimal getHolidayPay() { return holidayPay; }
    public BigDecimal getTotalPay() { return totalPay; }

    @Override
    public String toString() {
        return String.format(
                "%-10s | hours=%5.1f (reg=%5.1f, OT=%4.1f, PH=%4.1f) | RM%.2f + RM%.2f OT + RM%.2f PH = RM%.2f",
                employeeName, totalHours, regularHours, overtimeHours, holidayHours,
                regularPay, overtimePay, holidayPay, totalPay);
    }
}
