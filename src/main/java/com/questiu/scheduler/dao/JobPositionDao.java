package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.JobPosition;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /** Every position, lowest rank first within each department. */
    public List<JobPosition> findAll() throws SQLException {
        String sql = "SELECT job_position_id, role_id, position_name, rank_level " +
                     "FROM job_position ORDER BY role_id, rank_level, position_name";
        List<JobPosition> positions = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                positions.add(new JobPosition(
                        rs.getInt("job_position_id"),
                        rs.getInt("role_id"),
                        rs.getString("position_name"),
                        rs.getInt("rank_level")));
            }
        }
        return positions;
    }
}
