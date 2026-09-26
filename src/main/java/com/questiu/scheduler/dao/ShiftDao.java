package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.Shift;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads shift_definition rows (smartshift schema): the recurring weekly
 * shift template. Unlike the earlier week_plan-scoped design, this table
 * is NOT tied to a specific week - the same template is reused by every
 * schedule run, so there is no weekPlanId parameter here any more.
 */
public class ShiftDao {

    public List<Shift> findAll() throws SQLException {
        String sql = "SELECT shift_id, role_id, day_of_week, start_time, end_time, required_staff " +
                     "FROM shift_definition";
        List<Shift> shifts = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                shifts.add(new Shift(
                        rs.getInt("shift_id"),
                        DayOfWeekMapper.toInt(rs.getString("day_of_week")),
                        rs.getTime("start_time").toLocalTime(),
                        rs.getTime("end_time").toLocalTime(),
                        rs.getInt("role_id"),
                        rs.getInt("required_staff")
                ));
            }
        }
        return shifts;
    }
}
