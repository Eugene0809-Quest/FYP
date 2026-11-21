package com.questiu.scheduler.solver;

import java.math.BigDecimal;

/**
 * Turns what an administrator typed on the Solver Weights screen into SolverWeights,
 * with a plain-language error when something cannot be used. Kept free of JavaFX so it
 * can be tested on its own.
 */
public final class SolverWeightsParser {

    /** Either weights (error == null) or a sentence saying what to fix (weights == null). */
    public static final class Result {
        public final SolverWeights weights;
        public final String error;

        private Result(SolverWeights weights, String error) {
            this.weights = weights;
            this.error = error;
        }
    }

    private SolverWeightsParser() {}

    public static Result parse(String unfilled, String workload, String preference,
                               String overtime, String costMultiplier) {
        try {
            double unfilledRm = number("Price of an unfilled position", unfilled).doubleValue();
            long workloadRm = whole("Fairness (workload balance)", workload);
            double preferenceRm = number("Preference", preference).doubleValue();
            long overtimeRm = whole("Overtime", overtime);
            double cost = number("Cost multiplier", costMultiplier).doubleValue();
            SolverWeights w = new SolverWeights(unfilledRm, workloadRm, preferenceRm, overtimeRm, cost);
            String problem = w.problem();
            return problem == null ? new Result(w, null) : new Result(null, problem);
        } catch (IllegalArgumentException ex) {
            return new Result(null, ex.getMessage());
        }
    }

    /** A weight as plain text for a text field: 2.0 -> "2", 12345.67 -> "12345.67". */
    public static String plain(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal number(String name, String text) {
        String t = text == null ? "" : text.trim().replace(",", "");
        if (t.isEmpty()) throw new IllegalArgumentException(name + ": enter a value.");
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + ": \"" + text.trim() + "\" is not a number.");
        }
    }

    private static long whole(String name, String text) {
        BigDecimal v = number(name, text);
        try {
            return v.longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(name + ": must be a whole number of ringgit (no cents).");
        }
    }
}
