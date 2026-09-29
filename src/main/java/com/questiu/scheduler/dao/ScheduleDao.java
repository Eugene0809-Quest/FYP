package com.questiu.scheduler.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

/**
 * Creates one 'schedule' row per roster run (Automatic or Manual), so
 * schedule_assignment and payroll_record have a schedule_id to attach to
 * (Section 3.4 - "a run of the solver"). This is the step-4 DB-persistence
 * work: before this, Main.java/MainApp.java only printed/displayed results.
 */
public class ScheduleDao {

    /**
     * @param weekStartDate the Monday of the roster week (matches Shift.actualDate's convention)
     * @param status        'GENERATED' for Automatic (CP-SAT) mode, 'DRAFT' for a manually-built roster
     * @param objectiveValue the solver's objective value for Automatic mode, or null for Manual mode
     *                       (a hand-built roster has no CP-SAT objective to report)
     * @param generatedByUserId the logged-in manager's user_id (User.getUserId()), or null if unknown
     * @return the generated schedule_id
     */
    public int create(LocalDate weekStartDate, String status, Double objectiveValue,
                       Integer generatedByUserId) throws SQLException {
        String sql = "INSERT INTO schedule (week_start_date, status, objective_value, generated_by) " +
                     "VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setDate(1, Date.valueOf(weekStartDate));
            ps.setString(2, status);
            if (objectiveValue != null) {
                ps.setBigDecimal(3, BigDecimal.valueOf(objectiveValue));
            } else {
                ps.setNull(3, java.sql.Types.DECIMAL);
            }
            if (generatedByUserId != null) {
                ps.setInt(4, generatedByUserId);
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Failed to create schedule row - no generated key returned.");
    }
}
