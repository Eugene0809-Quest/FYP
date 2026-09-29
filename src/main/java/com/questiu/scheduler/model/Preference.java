package com.questiu.scheduler.model;

/**
 * One stored shift preference (employee_preference row): how much an
 * employee wants or wants to avoid a specific shift.
 * level: -2 = strongly avoid, -1 = avoid, +1 = prefer, +2 = strongly prefer.
 * Neutral (0) is never stored - no row means neutral.
 */
public class Preference {
    private final int employeeId;
    private final int shiftId;
    private final int level;

    public Preference(int employeeId, int shiftId, int level) {
        this.employeeId = employeeId;
        this.shiftId = shiftId;
        this.level = level;
    }

    public int getEmployeeId() { return employeeId; }
    public int getShiftId() { return shiftId; }
    public int getLevel() { return level; }
}
