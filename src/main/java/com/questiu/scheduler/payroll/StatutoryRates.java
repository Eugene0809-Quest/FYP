package com.questiu.scheduler.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * EPF/SOCSO/EIS statutory rate constants (supervisor item #5), sourced from
 * KWSP's Third Schedule and PERKESO's contribution rates current as of 2026.
 *
 * SIMPLIFICATIONS (flag for the report's scope section, same style as the
 * existing "Known simplifications" list and the item #4 holiday-pay note):
 *  - These are flat percentages, not the official wage-band lookup tables
 *    both agencies actually use (KWSP's Third Schedule, PERKESO's
 *    contribution table), which round slightly differently at each band's
 *    edges - a real payslip may differ by a few sen from this estimate.
 *  - Every employee is assumed to be a Malaysian citizen under 60 on
 *    standard EPF terms and SOCSO Category 1 (Employment Injury +
 *    Invalidity). Senior-citizen rates (60+), foreign-worker rates (2%/2%
 *    EPF, 1.25% employer-only SOCSO), and PCB income tax are NOT modelled -
 *    PCB in particular remains explicitly out of scope per the checkpoint
 *    deck; only EPF/SOCSO/EIS were brought into scope for item #5.
 */
public final class StatutoryRates {
    private StatutoryRates() {}

    public static final BigDecimal EPF_EMPLOYEE_RATE = new BigDecimal("0.11");
    public static final BigDecimal EPF_EMPLOYER_RATE_LOWER = new BigDecimal("0.13"); // monthly wage <= threshold
    public static final BigDecimal EPF_EMPLOYER_RATE_UPPER = new BigDecimal("0.12"); // monthly wage > threshold
    public static final BigDecimal EPF_EMPLOYER_TIER_THRESHOLD = new BigDecimal("5000.00"); // RM/month

    public static final BigDecimal SOCSO_EMPLOYEE_RATE = new BigDecimal("0.005");
    public static final BigDecimal SOCSO_EMPLOYER_RATE = new BigDecimal("0.0175");

    public static final BigDecimal EIS_EMPLOYEE_RATE = new BigDecimal("0.002");
    public static final BigDecimal EIS_EMPLOYER_RATE = new BigDecimal("0.002");

    /** Shared SOCSO/EIS monthly wage ceiling (raised from RM4,000 to RM6,000 in October 2024). */
    public static final BigDecimal SOCSO_EIS_MONTHLY_CEILING = new BigDecimal("6000.00");

    /** Used only to estimate a monthly wage from one week's payroll run, for the two threshold
     *  decisions above - see PayrollCalculator.computeStatutory for why. */
    public static final BigDecimal WEEKS_PER_MONTH =
            new BigDecimal("52").divide(new BigDecimal("12"), 10, RoundingMode.HALF_UP);
}
