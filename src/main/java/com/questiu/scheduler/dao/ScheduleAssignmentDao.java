package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.RosterAssignment;
import com.questiu.scheduler.model.Shift;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Persists a solved/assigned roster's employee<->shift pairings into
 * schedule_assignment (Section 3.4 - solver output), for a given schedule_id
 * from ScheduleDao.create(). Works identically for Automatic (CP-SAT) and
 * Manual mode, since both ultimately produce a List<RosterAssignment>.
 */
public class ScheduleAssignmentDao {

    public void saveAll(int scheduleId, List<RosterAssignment> assignments,
                         Map<Integer, Shift> shiftsById) throws SQLException {
        String sql = "INSERT INTO schedule_assignment (schedule_id, shift_id, employee_id, assigned_hours) " +
                     "VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (RosterAssignment a : assignments) {
                Shift shift = shiftsById.get(a.getShiftId());
                ps.setInt(1, scheduleId);
                ps.setInt(2, a.getShiftId());
                ps.setInt(3, a.getEmployeeId());
                ps.setBigDecimal(4, java.math.BigDecimal.valueOf(shift.durationHours())
                        .setScale(2, java.math.RoundingMode.HALF_UP));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
