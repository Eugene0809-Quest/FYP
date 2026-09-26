package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.Availability;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads availability rows (smartshift schema): recurring weekly
 * availability per employee, not scoped to any week_plan_id - matching
 * the Availability model, which never carried a week reference.
 */
public class AvailabilityDao {

    public List<Availability> findAll() throws SQLException {
        String sql = "SELECT employee_id, day_of_week, start_time, end_time FROM availability";
        return query(sql, ps -> {});
    }

    /** Availability rows for one employee - used to prefill the "Edit Availability" dialog. */
    public List<Availability> findByEmployeeId(int employeeId) throws SQLException {
        String sql = "SELECT employee_id, day_of_week, start_time, end_time FROM availability WHERE employee_id = ?";
        return query(sql, ps -> ps.setInt(1, employeeId));
    }

    private interface ParamSetter {
        void set(PreparedStatement ps) throws SQLException;
    }

    private List<Availability> query(String sql, ParamSetter setter) throws SQLException {
        List<Availability> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setter.set(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Availability(
                            rs.getInt("employee_id"),
                            DayOfWeekMapper.toInt(rs.getString("day_of_week")),
                            rs.getTime("start_time").toLocalTime(),
                            rs.getTime("end_time").toLocalTime()
                    ));
                }
            }
        }
        return result;
    }

    /**
     * Replaces all availability rows for one employee with the given list
     * (delete-then-insert in one transaction). Used by the "Edit
     * Availability" dialog (Section 3.7 - added because newly-registered
     * employees otherwise had zero availability rows and were permanently
     * unschedulable, since the solver's availability constraint hard-blocks
     * assignment when no matching row exists).
     */
    public void replaceForEmployee(int employeeId, List<Availability> newAvailability) throws SQLException {
        String deleteSql = "DELETE FROM availability WHERE employee_id = ?";
        String insertSql = "INSERT INTO availability (employee_id, day_of_week, start_time, end_time) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                    del.setInt(1, employeeId);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                    for (Availability a : newAvailability) {
                        ins.setInt(1, a.getEmployeeId());
                        ins.setString(2, DayOfWeekMapper.toCode(a.getDayOfWeek()));
                        ins.setTime(3, Time.valueOf(a.getStartTime()));
                        ins.setTime(4, Time.valueOf(a.getEndTime()));
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

