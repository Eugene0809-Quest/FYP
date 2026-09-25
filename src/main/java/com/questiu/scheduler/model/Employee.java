package com.questiu.scheduler.model;

import java.math.BigDecimal;

/**
 * Represents a single employee record (Chapter 3, Section 3.3 - Data layer).
 */
public class Employee {
    private final int employeeId;
    private final String fullName;
    private final int roleId;
    private final BigDecimal hourlyRate;
    private final BigDecimal maxWeeklyHours;
    private final boolean active;

    public Employee(int employeeId, String fullName, int roleId,
                     BigDecimal hourlyRate, BigDecimal maxWeeklyHours, boolean active) {
        this.employeeId = employeeId;
        this.fullName = fullName;
        this.roleId = roleId;
        this.hourlyRate = hourlyRate;
        this.maxWeeklyHours = maxWeeklyHours;
        this.active = active;
    }

    public int getEmployeeId() { return employeeId; }
    public String getFullName() { return fullName; }
    public int getRoleId() { return roleId; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public BigDecimal getMaxWeeklyHours() { return maxWeeklyHours; }
    public boolean isActive() { return active; }

    @Override
    public String toString() {
        return String.format("Employee{id=%d, name='%s', roleId=%d, rate=%s}",
                employeeId, fullName, roleId, hourlyRate);
    }
}
