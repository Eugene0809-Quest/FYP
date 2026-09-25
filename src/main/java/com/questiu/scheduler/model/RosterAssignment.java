package com.questiu.scheduler.model;

/** One solved x[e,s] = 1 entry: employee e is assigned to shift s. */
public class RosterAssignment {
    private final int employeeId;
    private final int shiftId;

    public RosterAssignment(int employeeId, int shiftId) {
        this.employeeId = employeeId;
        this.shiftId = shiftId;
    }

    public int getEmployeeId() { return employeeId; }
    public int getShiftId() { return shiftId; }

    @Override
    public String toString() {
        return String.format("Assignment{emp=%d, shift=%d}", employeeId, shiftId);
    }
}
