package com.questiu.scheduler.model;

import java.time.LocalTime;
import java.time.Duration;

/**
 * A single shift slot to be filled (Section 3.4 - decision problem input).
 * dayOfWeek: 1 = Monday ... 7 = Sunday
 */
public class Shift {
    private final int shiftId;
    private final int weekPlanId;
    private final int dayOfWeek;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final int requiredRoleId;
    private final int staffNeeded;

    public Shift(int shiftId, int weekPlanId, int dayOfWeek, LocalTime startTime,
                 LocalTime endTime, int requiredRoleId, int staffNeeded) {
        this.shiftId = shiftId;
        this.weekPlanId = weekPlanId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.requiredRoleId = requiredRoleId;
        this.staffNeeded = staffNeeded;
    }

    public int getShiftId() { return shiftId; }
    public int getWeekPlanId() { return weekPlanId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getRequiredRoleId() { return requiredRoleId; }
    public int getStaffNeeded() { return staffNeeded; }

    /** Duration of this shift in hours (e.g. 4.0 for an 11:00-15:00 shift). */
    public double durationHours() {
        return Duration.between(startTime, endTime).toMinutes() / 60.0;
    }

    /** True if this shift's time window overlaps with another shift on the same day. */
    public boolean overlapsWith(Shift other) {
        if (this.dayOfWeek != other.dayOfWeek) return false;
        return this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
    }

    @Override
    public String toString() {
        return String.format("Shift{id=%d, day=%d, %s-%s, role=%d, need=%d}",
                shiftId, dayOfWeek, startTime, endTime, requiredRoleId, staffNeeded);
    }
}
