package com.questiu.scheduler.solver;

import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.Shift;

import java.math.RoundingMode;
import java.time.Duration;

/**
 * Cost arithmetic for the labour-cost term of the scheduling objective
 * (Section 3.4.4), kept apart from SchedulingEngine so it can be tested
 * without the OR-Tools native library.
 *
 * Unit: 1 sen (RM0.01), because CP-SAT works with integers only. The cost of
 * putting employee e on shift s is what PayrollCalculator would pay for that
 * shift: rate x hours, doubled when the shift falls on a public holiday. Every
 * other objective weight is read in the same unit (a weight of 200 on a
 * preference level means RM2.00 per level).
 *
 * This is GROSS pay only. Employer EPF/SOCSO/EIS are not included: they are not
 * proportional to gross (the EPF employer rate changes at RM5,000 a month and
 * SOCSO/EIS have a ceiling), so minimising gross pay is close to, but not
 * exactly, minimising employer total cost. Overtime is not needed here: in
 * automatic mode nobody can exceed their cap, so there is no overtime pay.
 */
public final class LabourCost {

    /** Must match PayrollCalculator's public-holiday multiplier (LabourCostTest checks they agree). */
    public static final int HOLIDAY_MULTIPLIER = 2;

    /** Floor for the price of one unfilled position: RM10,000. */
    public static final long MIN_UNFILLED_PENALTY = 1_000_000L;

    private LabourCost() {}

    /** Cost in sen of employee e working shift s; publicHoliday doubles it. */
    public static long positionCostSen(Employee e, Shift s, boolean publicHoliday) {
        long rateSen = e.getHourlyRate().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        long minutes = Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
        long multiplier = publicHoliday ? HOLIDAY_MULTIPLIER : 1;
        return Math.round(rateSen * minutes * multiplier / 60.0);
    }

    /**
     * Price of leaving one position unfilled. It must always be larger than the
     * cost of filling it, otherwise the solver would rather leave a shift empty
     * than pay for it (the same trap as the old 40-hour overtime threshold, see
     * SchedulingEngineCoverageTest). So it is at least RM10,000 and, for very
     * expensive shifts, ten times the dearest single position.
     */
    public static long unfilledPenalty(long maxPositionCostSen) {
        return unfilledPenalty(maxPositionCostSen, MIN_UNFILLED_PENALTY);
    }

    /**
     * Same safety rule with a configured price (sen): the configured price is used unless
     * it is lower than ten times the dearest position, so a wrongly small setting can never
     * make the solver prefer an empty shift to a staffed one.
     */
    public static long unfilledPenalty(long maxPositionCostSen, long configuredSen) {
        return Math.max(configuredSen, 10L * maxPositionCostSen);
    }
}
