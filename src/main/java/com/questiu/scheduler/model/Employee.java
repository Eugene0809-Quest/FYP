package com.questiu.scheduler.model;

import java.math.BigDecimal;

/**
 * Represents a single employee record (Chapter 3, Section 3.3 - Data layer).
 * bankAccountNumber and employmentType were added per supervisor feedback
 * on the FYP1 checkpoint, to support a real registration screen.
 */
public class Employee {
    private final int employeeId;
    private final String fullName;
    private final int roleId;
    private final BigDecimal hourlyRate;
    private final BigDecimal maxWeeklyHours;
    private final boolean active;
    private final String bankAccountNumber;
    private final EmploymentType employmentType;

    public Employee(int employeeId, String fullName, int roleId,
                     BigDecimal hourlyRate, BigDecimal maxWeeklyHours, boolean active,
                     String bankAccountNumber, EmploymentType employmentType) {
        this.employeeId = employeeId;
        this.fullName = fullName;
        this.roleId = roleId;
        this.hourlyRate = hourlyRate;
        this.maxWeeklyHours = maxWeeklyHours;
        this.active = active;
        this.bankAccountNumber = bankAccountNumber;
        this.employmentType = employmentType;
    }

    public int getEmployeeId() { return employeeId; }
    public String getFullName() { return fullName; }
    public int getRoleId() { return roleId; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public BigDecimal getMaxWeeklyHours() { return maxWeeklyHours; }
    public boolean isActive() { return active; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    public EmploymentType getEmploymentType() { return employmentType; }

    @Override
    public String toString() {
        return String.format("Employee{id=%d, name='%s', roleId=%d, rate=%s, type=%s}",
                employeeId, fullName, roleId, hourlyRate, employmentType);
    }
}
