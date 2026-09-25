package com.questiu.scheduler.model;

import java.time.LocalTime;

/** An employee-declared window of availability for one day of the week plan. */
public class Availability {
    private final int employeeId;
    private final int dayOfWeek;
    private final LocalTime startTime;
    private final LocalTime endTime;

    public Availability(int employeeId, int dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.employeeId = employeeId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getEmployeeId() { return employeeId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }

    /** True if the given shift falls entirely within this availability window. */
    public boolean covers(Shift shift) {
        return shift.getDayOfWeek() == dayOfWeek
                && !shift.getStartTime().isBefore(startTime)
                && !shift.getEndTime().isAfter(endTime);
    }
}
