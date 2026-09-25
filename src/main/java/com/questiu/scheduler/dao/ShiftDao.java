package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.Shift;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class ShiftDao {

    public List<Shift> findByWeekPlan(int weekPlanId) throws SQLException {
        String sql = "SELECT shift_id, week_plan_id, day_of_week, start_time, end_time, " +
                     "required_role_id, staff_needed FROM shift WHERE week_plan_id = ?";
        List<Shift> shifts = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, weekPlanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    shifts.add(new Shift(
                            rs.getInt("shift_id"),
                            rs.getInt("week_plan_id"),
                            rs.getInt("day_of_week"),
                            rs.getTime("start_time").toLocalTime(),
                            rs.getTime("end_time").toLocalTime(),
                            rs.getInt("required_role_id"),
                            rs.getInt("staff_needed")
                    ));
                }
            }
        }
        return shifts;
    }
}
