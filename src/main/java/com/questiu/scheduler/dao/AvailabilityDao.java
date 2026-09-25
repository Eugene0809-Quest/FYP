package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.Availability;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AvailabilityDao {

    public List<Availability> findByWeekPlan(int weekPlanId) throws SQLException {
        String sql = "SELECT employee_id, day_of_week, start_time, end_time " +
                     "FROM availability WHERE week_plan_id = ?";
        List<Availability> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, weekPlanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Availability(
                            rs.getInt("employee_id"),
                            rs.getInt("day_of_week"),
                            rs.getTime("start_time").toLocalTime(),
                            rs.getTime("end_time").toLocalTime()
                    ));
                }
            }
        }
        return result;
    }
}
