package com.questiu.scheduler.model;

import java.math.BigDecimal;

/**
 * One employee's payroll result for a week plan (Section 3.5).
 * Produced by PayrollCalculator directly from the solved roster - never entered
 * or edited manually, which is what keeps roster and payroll from drifting apart.
 */
public class PayrollRecord {
    private final int employeeId;
    private final String employeeName;
    private final double totalHours;
    private final double regularHours;
    private final double overtimeHours;
    private final BigDecimal regularPay;
    private final BigDecimal overtimePay;
    private final BigDecimal totalPay;

    public PayrollRecord(int employeeId, String employeeName, double totalHours,
                          double regularHours, double overtimeHours,
                          BigDecimal regularPay, BigDecimal overtimePay, BigDecimal totalPay) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.totalHours = totalHours;
        this.regularHours = regularHours;
        this.overtimeHours = overtimeHours;
        this.regularPay = regularPay;
        this.overtimePay = overtimePay;
        this.totalPay = totalPay;
    }

    public int getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public double getTotalHours() { return totalHours; }
    public double getRegularHours() { return regularHours; }
    public double getOvertimeHours() { return overtimeHours; }
    public BigDecimal getRegularPay() { return regularPay; }
    public BigDecimal getOvertimePay() { return overtimePay; }
    public BigDecimal getTotalPay() { return totalPay; }

    @Override
    public String toString() {
        return String.format("%-10s | hours=%5.1f (reg=%5.1f, OT=%4.1f) | RM%.2f + RM%.2f OT = RM%.2f",
                employeeName, totalHours, regularHours, overtimeHours, regularPay, overtimePay, totalPay);
    }
}
