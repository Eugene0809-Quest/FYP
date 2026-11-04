package com.questiu.scheduler.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * One leave request (leave_request table, migration 007). A staff member asks
 * for dated time off and an administrator approves or rejects it. employeeName
 * and deciderName come from joins and are only filled when read from the
 * database.
 */
public class LeaveRequest {
    private final int leaveId;
    private final int employeeId;
    private final String employeeName;
    private final LeaveType leaveType;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String reason;
    private final LeaveStatus status;
    private final LocalDateTime requestedAt;
    private final String deciderName;
    private final LocalDateTime decidedAt;
    private final String adminNote;

    public LeaveRequest(int leaveId, int employeeId, String employeeName, LeaveType leaveType,
                        LocalDate startDate, LocalDate endDate, String reason, LeaveStatus status,
                        LocalDateTime requestedAt, String deciderName, LocalDateTime decidedAt,
                        String adminNote) {
        this.leaveId = leaveId;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.status = status;
        this.requestedAt = requestedAt;
        this.deciderName = deciderName;
        this.decidedAt = decidedAt;
        this.adminNote = adminNote;
    }

    public int getLeaveId() { return leaveId; }
    public int getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public LeaveType getLeaveType() { return leaveType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getReason() { return reason; }
    public LeaveStatus getStatus() { return status; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public String getDeciderName() { return deciderName; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public String getAdminNote() { return adminNote; }

    /** Number of calendar days covered, counting both the first and the last day. */
    public long getDays() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
