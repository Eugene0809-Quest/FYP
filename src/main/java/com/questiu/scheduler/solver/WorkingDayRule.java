package com.questiu.scheduler.solver;

/**
 * The weekly rest-day rule shared by the solver (hard constraint 6) and the compliance audit.
 *
 * The Employment Act 1955 (s. 59) gives every employee one whole rest day in each week, so a
 * roster may put nobody on shifts on all seven days. The same number is used by
 * SchedulingEngine (to forbid it) and ComplianceChecker (to detect it in a manual roster).
 */
public final class WorkingDayRule {

    /** A week has 7 days and one of them must be a rest day. */
    public static final int MAX_WORKING_DAYS_PER_WEEK = 6;

    private WorkingDayRule() {}
}
