package com.questiu.scheduler.model;

/** Kind of leave (leave_request.leave_type). toString() gives the label shown in dropdowns. */
public enum LeaveType {
    ANNUAL("Annual leave"),
    SICK("Sick leave"),
    EMERGENCY("Emergency leave"),
    UNPAID("Unpaid leave"),
    OTHER("Other");

    private final String label;

    LeaveType(String label) {
        this.label = label;
    }

    public String label() { return label; }

    @Override
    public String toString() { return label; }
}
