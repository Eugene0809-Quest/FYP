package com.questiu.scheduler.model;

import java.math.BigDecimal;

/**
 * One employee's payroll result for a week plan (Section 3.5, extended for
 * item #4's public holiday premium and item #5's EPF/SOCSO/EIS statutory
 * contributions). Produced by PayrollCalculator directly from the
 * solved/assigned roster - never entered or edited manually, which is what
 * keeps roster and payroll from drifting apart (Section 2.2.3).
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
    private final StatutoryBreakdown statutory;

    public PayrollRecord(int employeeId, String employeeName, double totalHours,
                          double regularHours, double overtimeHours, double holidayHours,
                          BigDecimal regularPay, BigDecimal overtimePay, BigDecimal holidayPay,
                          BigDecimal totalPay, StatutoryBreakdown statutory) {
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
        this.statutory = statutory;
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
    public StatutoryBreakdown getStatutory() { return statutory; }

    @Override
    public String toString() {
        return String.format(
                "%-10s | hours=%5.1f (reg=%5.1f, OT=%4.1f, PH=%4.1f) | gross RM%.2f | EPF -RM%.2f | SOCSO -RM%.2f | EIS -RM%.2f | net RM%.2f",
                employeeName, totalHours, regularHours, overtimeHours, holidayHours, totalPay,
                statutory.getEmployeeEpf(), statutory.getEmployeeSocso(), statutory.getEmployeeEis(),
                statutory.getNetPay());
    }

    /**
     * EPF/SOCSO/EIS statutory contributions (supervisor item #5), computed
     * from the employee-side wage base (regular pay + holiday pay; overtime
     * is excluded, matching KWSP's "payments not subject to EPF" guidance -
     * see StatutoryRates for the full simplification notes and exact rates).
     */
    public static class StatutoryBreakdown {
        private final BigDecimal employeeEpf;
        private final BigDecimal employerEpf;
        private final BigDecimal employeeSocso;
        private final BigDecimal employerSocso;
        private final BigDecimal employeeEis;
        private final BigDecimal employerEis;
        private final BigDecimal netPay;
        private final BigDecimal employerTotalCost;

        public StatutoryBreakdown(BigDecimal employeeEpf, BigDecimal employerEpf,
                                   BigDecimal employeeSocso, BigDecimal employerSocso,
                                   BigDecimal employeeEis, BigDecimal employerEis,
                                   BigDecimal netPay, BigDecimal employerTotalCost) {
            this.employeeEpf = employeeEpf;
            this.employerEpf = employerEpf;
            this.employeeSocso = employeeSocso;
            this.employerSocso = employerSocso;
            this.employeeEis = employeeEis;
            this.employerEis = employerEis;
            this.netPay = netPay;
            this.employerTotalCost = employerTotalCost;
        }

        public BigDecimal getEmployeeEpf() { return employeeEpf; }
        public BigDecimal getEmployerEpf() { return employerEpf; }
        public BigDecimal getEmployeeSocso() { return employeeSocso; }
        public BigDecimal getEmployerSocso() { return employerSocso; }
        public BigDecimal getEmployeeEis() { return employeeEis; }
        public BigDecimal getEmployerEis() { return employerEis; }
        /** Gross pay minus employee-side EPF + SOCSO + EIS (no PCB income tax - out of scope). */
        public BigDecimal getNetPay() { return netPay; }
        /** Gross pay plus employer-side EPF + SOCSO + EIS - the true weekly labour cost to the business. */
        public BigDecimal getEmployerTotalCost() { return employerTotalCost; }
    }
}
