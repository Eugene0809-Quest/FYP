package com.questiu.scheduler.dao;

import com.questiu.scheduler.solver.SolverWeights;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the solver's objective weights, which live in the soft rows of
 * constraint_rule (migration 010). Units are ringgit, see SolverWeights.
 *
 * load() never throws and never changes the solver's behaviour by accident: if the
 * migration has not been applied (no LABOUR_COST row), or a value is missing or not
 * allowed, the built-in default for that weight is used and a plain-language note
 * says so. That keeps the program safe to run before or after the migration.
 */
public class SolverWeightsDao {

    public static final String OVERTIME = "OVERTIME";
    public static final String WORKLOAD = "WORKLOAD";
    public static final String PREFERENCE = "PREFERENCE";
    public static final String UNFILLED = "UNFILLED";
    /** Its presence marks migration 010 as applied. */
    public static final String LABOUR_COST = "LABOUR_COST";

    /** The weights to hand to the solver, plus a note (null when everything was read cleanly). */
    public static final class Loaded {
        public final SolverWeights weights;
        public final String note;

        Loaded(SolverWeights weights, String note) {
            this.weights = weights;
            this.note = note;
        }
    }

    public Loaded load() {
        String sql = "SELECT rule_code, weight FROM constraint_rule WHERE rule_code IN (?, ?, ?, ?, ?)";
        Map<String, BigDecimal> byCode = new HashMap<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, OVERTIME);
            ps.setString(2, WORKLOAD);
            ps.setString(3, PREFERENCE);
            ps.setString(4, UNFILLED);
            ps.setString(5, LABOUR_COST);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) byCode.put(rs.getString("rule_code"), rs.getBigDecimal("weight"));
            }
        } catch (SQLException ex) {
            return new Loaded(SolverWeights.defaults(),
                    "Could not read the solver weights (" + ex.getMessage() + "); built-in defaults were used.");
        }
        return fromRules(byCode);
    }

    /**
     * Turns the stored rule weights into SolverWeights. Public so it can be tested without a
     * database. Anything missing or not allowed falls back to the default for that weight.
     */
    public static Loaded fromRules(Map<String, BigDecimal> byCode) {
        SolverWeights d = SolverWeights.defaults();
        if (!byCode.containsKey(LABOUR_COST)) {
            return new Loaded(d, "The solver weights are not set up in the database yet "
                    + "(migration 010 not applied); built-in defaults were used.");
        }
        List<String> notes = new ArrayList<>();
        double unfilled = money(byCode, UNFILLED, d.getUnfilledRm(), notes);
        double preference = money(byCode, PREFERENCE, d.getPreferenceRmPerLevel(), notes);
        double cost = money(byCode, LABOUR_COST, d.getCostMultiplier(), notes);
        long workload = whole(byCode, WORKLOAD, d.getWorkloadRmPerHour(), notes);
        long overtime = whole(byCode, OVERTIME, d.getOvertimeRmPerHour(), notes);

        SolverWeights w = new SolverWeights(unfilled, workload, preference, overtime, cost);
        String problem = w.problem();
        if (problem != null) {
            return new Loaded(d, "Stored solver weights are not allowed (" + problem
                    + "); built-in defaults were used.");
        }
        return new Loaded(w, notes.isEmpty() ? null
                : String.join(" ", notes) + " Built-in defaults were used for those.");
    }

    private static double money(Map<String, BigDecimal> byCode, String code, double fallback, List<String> notes) {
        BigDecimal v = byCode.get(code);
        if (v == null) {
            notes.add("Weight " + code + " is missing.");
            return fallback;
        }
        return v.doubleValue();
    }

    private static long whole(Map<String, BigDecimal> byCode, String code, long fallback, List<String> notes) {
        BigDecimal v = byCode.get(code);
        if (v == null) {
            notes.add("Weight " + code + " is missing.");
            return fallback;
        }
        try {
            return v.longValueExact();
        } catch (ArithmeticException notWhole) {
            notes.add("Weight " + code + " must be a whole number of ringgit.");
            return fallback;
        }
    }

    /**
     * Saves all five weights in one transaction (used by the administrator's weights screen).
     * Refuses weights that are not allowed, and fails (changing nothing) if a rule row is missing.
     */
    public void save(SolverWeights w) throws SQLException {
        String problem = w.problem();
        if (problem != null) throw new IllegalArgumentException(problem);
        String sql = "UPDATE constraint_rule SET weight = ? WHERE rule_code = ?";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                update(ps, OVERTIME, BigDecimal.valueOf(w.getOvertimeRmPerHour()));
                update(ps, WORKLOAD, BigDecimal.valueOf(w.getWorkloadRmPerHour()));
                update(ps, PREFERENCE, BigDecimal.valueOf(w.getPreferenceRmPerLevel()));
                update(ps, UNFILLED, BigDecimal.valueOf(w.getUnfilledRm()));
                update(ps, LABOUR_COST, BigDecimal.valueOf(w.getCostMultiplier()));
                conn.commit();
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private static void update(PreparedStatement ps, String code, BigDecimal value) throws SQLException {
        ps.setBigDecimal(1, value.setScale(2, RoundingMode.HALF_UP));
        ps.setString(2, code);
        if (ps.executeUpdate() != 1) {
            throw new IllegalStateException("The rule " + code + " is missing from constraint_rule "
                    + "(has migration 010 been applied?). Nothing was changed.");
        }
    }
}
