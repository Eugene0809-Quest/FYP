package com.questiu.scheduler.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a single employee record (Chapter 3, Section 3.3 - Data layer).
 * bankAccountNumber and employmentType were added per supervisor feedback
 * on the FYP1 checkpoint, to support a real registration screen.
 *
 * FYP2 (migrations 007 and 008): jobPositionId, icNumber, epfNumber,
 * socsoNumber, eisNumber and dateJoined were added after the lecturer asked
 * for member details and statutory numbers. They can all be null (existing
 * employees, or employees registered before the new fields were filled in).
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
    private final Integer jobPositionId;
    private final String icNumber;
    private final String epfNumber;
    private final String socsoNumber;
    private final String eisNumber;
    private final LocalDate dateJoined;

    public Employee(int employeeId, String fullName, int roleId,
                     BigDecimal hourlyRate, BigDecimal maxWeeklyHours, boolean active,
                     String bankAccountNumber, EmploymentType employmentType,
                     Integer jobPositionId, String icNumber, String epfNumber,
                     String socsoNumber, String eisNumber, LocalDate dateJoined) {
        this.employeeId = employeeId;
        this.fullName = fullName;
        this.roleId = roleId;
        this.hourlyRate = hourlyRate;
        this.maxWeeklyHours = maxWeeklyHours;
        this.active = active;
        this.bankAccountNumber = bankAccountNumber;
        this.employmentType = employmentType;
        this.jobPositionId = jobPositionId;
        this.icNumber = icNumber;
        this.epfNumber = epfNumber;
        this.socsoNumber = socsoNumber;
        this.eisNumber = eisNumber;
        this.dateJoined = dateJoined;
    }

    /** Kept so existing code and tests that build an Employee the FYP1 way still compile. */
    public Employee(int employeeId, String fullName, int roleId,
                     BigDecimal hourlyRate, BigDecimal maxWeeklyHours, boolean active,
                     String bankAccountNumber, EmploymentType employmentType) {
        this(employeeId, fullName, roleId, hourlyRate, maxWeeklyHours, active,
                bankAccountNumber, employmentType, null, null, null, null, null, null);
    }

    public int getEmployeeId() { return employeeId; }
    public String getFullName() { return fullName; }
    public int getRoleId() { return roleId; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public BigDecimal getMaxWeeklyHours() { return maxWeeklyHours; }
    public boolean isActive() { return active; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public Integer getJobPositionId() { return jobPositionId; }
    public String getIcNumber() { return icNumber; }
    public String getEpfNumber() { return epfNumber; }
    public String getSocsoNumber() { return socsoNumber; }
    public String getEisNumber() { return eisNumber; }
    public LocalDate getDateJoined() { return dateJoined; }

    @Override
    public String toString() {
        return String.format("Employee{id=%d, name='%s', roleId=%d, rate=%s, type=%s}",
                employeeId, fullName, roleId, hourlyRate, employmentType);
    }
}
