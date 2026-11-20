package com.questiu.scheduler.solver;

/**
 * The tunable weights of the scheduling objective (Section 3.4.4), gathered in one
 * place so they can later be read from the database and edited by an administrator
 * instead of being fixed numbers inside SchedulingEngine.
 *
 * Every weight is expressed in ringgit so that a manager can read it:
 *  - unfilledRm            price of leaving one position empty (RM per position)
 *  - workloadRmPerHour     price of one hour of difference between the most and the
 *                          least loaded person in the same role (whole RM)
 *  - preferenceRmPerLevel  value of one preference level, from -2 to +2 (RM per level)
 *  - overtimeRmPerHour     price of an overtime hour (whole RM; inert in automatic mode
 *                          because nobody can exceed their cap)
 *  - costMultiplier        how much the wage bill counts: 1.0 = real pay, 0 = ignore pay
 *
 * Inside the solver the unit is the sen (RM0.01), because CP-SAT works with integers.
 * The workload and overtime terms are measured per hundredth of an hour, and RM per hour
 * is numerically the same as sen per hundredth of an hour, which is why those two are
 * whole numbers of ringgit.
 *
 * defaults() equals the numbers that were previously written into SchedulingEngine,
 * so a solve with the defaults gives the same objective as before.
 */
public final class SolverWeights {

    public static final double MAX_RM = 1_000_000;

    private final double unfilledRm;
    private final long workloadRmPerHour;
    private final double preferenceRmPerLevel;
    private final long overtimeRmPerHour;
    private final double costMultiplier;

    public SolverWeights(double unfilledRm, long workloadRmPerHour, double preferenceRmPerLevel,
                         long overtimeRmPerHour, double costMultiplier) {
        this.unfilledRm = unfilledRm;
        this.workloadRmPerHour = workloadRmPerHour;
        this.preferenceRmPerLevel = preferenceRmPerLevel;
        this.overtimeRmPerHour = overtimeRmPerHour;
        this.costMultiplier = costMultiplier;
    }

    /** The values the solver used before the weights became configurable. */
    public static SolverWeights defaults() {
        return new SolverWeights(10_000.0, 5, 2.0, 100, 1.0);
    }

    public double getUnfilledRm() { return unfilledRm; }
    public long getWorkloadRmPerHour() { return workloadRmPerHour; }
    public double getPreferenceRmPerLevel() { return preferenceRmPerLevel; }
    public long getOvertimeRmPerHour() { return overtimeRmPerHour; }
    public double getCostMultiplier() { return costMultiplier; }

    /** Objective coefficient (sen) of one unfilled position, before the safety floor in LabourCost. */
    public long unfilledSen() { return Math.round(unfilledRm * 100); }

    /** Objective coefficient (sen) of one preference level. */
    public long preferenceSenPerLevel() { return Math.round(preferenceRmPerLevel * 100); }

    /** Coefficient per hundredth of an hour of workload spread (same number as RM per hour). */
    public long workloadPerHundredthHour() { return workloadRmPerHour; }

    /** Coefficient per hundredth of an hour of overtime (same number as RM per hour). */
    public long overtimePerHundredthHour() { return overtimeRmPerHour; }

    /** A wage cost in sen as it counts in the objective. */
    public long scaleCost(long costSen) { return Math.round(costSen * costMultiplier); }

    /**
     * Null if every weight is allowed, otherwise a sentence saying what is wrong.
     * Used before weights are saved or handed to the solver.
     */
    public String problem() {
        if (!isFiniteInRange(unfilledRm, 0, MAX_RM)) return "Unfilled-position price must be between 0 and " + (long) MAX_RM + ".";
        if (workloadRmPerHour < 0 || workloadRmPerHour > 10_000) return "Workload weight must be a whole number from 0 to 10,000.";
        if (!isFiniteInRange(preferenceRmPerLevel, 0, 10_000)) return "Preference value must be between 0 and 10,000.";
        if (overtimeRmPerHour < 0 || overtimeRmPerHour > 10_000) return "Overtime weight must be a whole number from 0 to 10,000.";
        if (!isFiniteInRange(costMultiplier, 0, 100)) return "Cost multiplier must be between 0 and 100.";
        return null;
    }

    private static boolean isFiniteInRange(double v, double lo, double hi) {
        return !Double.isNaN(v) && !Double.isInfinite(v) && v >= lo && v <= hi;
    }

    @Override
    public String toString() {
        return String.format("SolverWeights{unfilled=RM%.2f, workload=RM%d/h, preference=RM%.2f/level, overtime=RM%d/h, cost x%.2f}",
                unfilledRm, workloadRmPerHour, preferenceRmPerLevel, overtimeRmPerHour, costMultiplier);
    }
}
