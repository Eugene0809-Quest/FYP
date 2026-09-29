package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.Preference;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads/writes employee_preference rows (smartshift schema). Only non-neutral
 * preferences (level -2..-1 and +1..+2) are stored; no row means neutral.
 */
public class PreferenceDao {

    public List<Preference> findAll() throws SQLException {
        return query("SELECT employee_id, shift_id, preference_level FROM employee_preference", null);
    }

    public List<Preference> findByEmployeeId(int employeeId) throws SQLException {
        return query("SELECT employee_id, shift_id, preference_level FROM employee_preference WHERE employee_id = ?",
                employeeId);
    }

    private List<Preference> query(String sql, Integer employeeId) throws SQLException {
        List<Preference> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (employeeId != null) ps.setInt(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Preference(
                            rs.getInt("employee_id"),
                            rs.getInt("shift_id"),
                            rs.getInt("preference_level")));
                }
            }
        }
        return result;
    }

    /** Replaces all of one employee's preferences in a single transaction (delete-then-insert). */
    public void replaceForEmployee(int employeeId, List<Preference> newPreferences) throws SQLException {
        String deleteSql = "DELETE FROM employee_preference WHERE employee_id = ?";
        String insertSql = "INSERT INTO employee_preference (employee_id, shift_id, preference_level) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                    del.setInt(1, employeeId);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                    for (Preference p : newPreferences) {
                        ins.setInt(1, p.getEmployeeId());
                        ins.setInt(2, p.getShiftId());
                        ins.setInt(3, p.getLevel());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
