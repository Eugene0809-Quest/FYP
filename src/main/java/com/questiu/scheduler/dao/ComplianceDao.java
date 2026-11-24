package com.questiu.scheduler.dao;

import com.questiu.scheduler.solver.ComplianceChecker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

/**
 * Saves the compliance audit of one saved roster (FYP2, item 5d-2):
 * one compliance_report row (totals) and one constraint_violation row per problem found.
 * Everything is written in one transaction, so a roster never ends up with half a report.
 */
public class ComplianceDao {

    /**
     * @param softPenaltyScore the preference violation score shown on screen (avoided shifts imposed)
     * @return how many constraint_violation rows were written
     * @throws IllegalStateException if a rule used by the report has no row in constraint_rule
     *         (for example LEAVE before migration 011); nothing is written in that case
     */
    public int save(int scheduleId, ComplianceChecker.Report report, double softPenaltyScore) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Integer> ruleIds = loadRuleIds(conn);
                for (ComplianceChecker.Violation v : report.violations) {
                    if (!ruleIds.containsKey(v.rule.code())) {
                        throw new IllegalStateException("The rule " + v.rule.code() + " is missing from constraint_rule "
                                + "(has migration 011 been applied?). Nothing was saved.");
                    }
                }
                insertReport(conn, scheduleId, report, softPenaltyScore);
                int rows = insertViolations(conn, scheduleId, report, ruleIds);
                conn.commit();
                return rows;
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private static Map<String, Integer> loadRuleIds(Connection conn) throws SQLException {
        Map<String, Integer> ids = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT rule_id, rule_code FROM constraint_rule");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.put(rs.getString("rule_code"), rs.getInt("rule_id"));
        }
        return ids;
    }

    private static void insertReport(Connection conn, int scheduleId, ComplianceChecker.Report report,
                                     double softPenaltyScore) throws SQLException {
        String sql = "INSERT INTO compliance_report (schedule_id, total_violations, hard_violations, "
                + "soft_penalty_score, coverage_percent) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, scheduleId);
            ps.setInt(2, report.totalViolations);
            ps.setInt(3, report.hardViolations);
            ps.setBigDecimal(4, BigDecimal.valueOf(softPenaltyScore).setScale(2, RoundingMode.HALF_UP));
            ps.setBigDecimal(5, BigDecimal.valueOf(report.coveragePercent()).setScale(2, RoundingMode.HALF_UP));
            ps.executeUpdate();
        }
    }

    private static int insertViolations(Connection conn, int scheduleId, ComplianceChecker.Report report,
                                        Map<String, Integer> ruleIds) throws SQLException {
        if (report.violations.isEmpty()) return 0;
        String sql = "INSERT INTO constraint_violation (schedule_id, rule_id, employee_id, shift_id, severity, description) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ComplianceChecker.Violation v : report.violations) {
                ps.setInt(1, scheduleId);
                ps.setInt(2, ruleIds.get(v.rule.code()));
                if (v.employeeId == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, v.employeeId);
                if (v.shiftId == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, v.shiftId);
                ps.setBigDecimal(5, BigDecimal.valueOf(v.severity).setScale(2, RoundingMode.HALF_UP));
                ps.setString(6, v.description);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return report.violations.size();
    }
}
