package com.questiu.scheduler.model;

/** Where a leave request stands (leave_request.status). */
public enum LeaveStatus {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled");

    private final String label;

    LeaveStatus(String label) {
        this.label = label;
    }

    public String label() { return label; }

    @Override
    public String toString() { return label; }
}
