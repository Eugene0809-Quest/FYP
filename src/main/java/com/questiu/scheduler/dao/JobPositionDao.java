package com.questiu.scheduler.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads job_position (migration 008): the job titles inside a department
 * (Cashier, Supervisor, Head Chef, ...). 'role' stays the department the
 * solver matches on; this table is only for titles and display.
 */
public class JobPositionDao {

    /** job_position_id -> position_name, in id order. */
    public Map<Integer, String> findNamesById() throws SQLException {
        String sql = "SELECT job_position_id, position_name FROM job_position ORDER BY job_position_id";
        Map<Integer, String> names = new LinkedHashMap<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.put(rs.getInt("job_position_id"), rs.getString("position_name"));
            }
        }
        return names;
    }
}
