package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.LeaveRequest;
import com.questiu.scheduler.model.LeaveStatus;
import com.questiu.scheduler.model.LeaveType;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes leave_request (migration 007). Every state change is a
 * single UPDATE guarded by the current status, so two people clicking at the
 * same time cannot both "win": the second update changes zero rows and the
 * method returns false.
 */
public class LeaveRequestDao {

    private static final String SELECT =
            "SELECT l.leave_id, l.employee_id, e.full_name AS employee_name, l.leave_type, " +
            "l.start_date, l.end_date, l.reason, l.status, l.requested_at, " +
            "u.full_name AS decider_name, l.decided_at, l.admin_note " +
            "FROM leave_request l " +
            "JOIN employee e ON e.employee_id = l.employee_id " +
            "LEFT JOIN user_account u ON u.user_id = l.decided_by ";

    /** Pending requests first (they need a decision), then newest first. */
    private static final String ORDER =
            "ORDER BY (l.status = 'PENDING') DESC, l.requested_at DESC, l.leave_id DESC";

    /** Every request - the administrator's view. pendingOnly hides decided and cancelled ones. */
    public List<LeaveRequest> findAll(boolean pendingOnly) throws SQLException {
        String sql = SELECT + (pendingOnly ? "WHERE l.status = 'PENDING' " : "") + ORDER;
        List<LeaveRequest> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    /**
     * Approved leave that touches any day from 'from' to 'to' (both included).
     * The roster uses this: approved leave in the roster week makes that person
     * unavailable on those days.
     */
    public List<LeaveRequest> findApprovedOverlapping(LocalDate from, LocalDate to) throws SQLException {
        String sql = SELECT + "WHERE l.status = 'APPROVED' AND l.start_date <= ? AND l.end_date >= ? " +
                     "ORDER BY l.start_date, l.leave_id";
        List<LeaveRequest> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(to));
            ps.setDate(2, Date.valueOf(from));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    /** One employee's own requests, newest first - the staff view. */
    public List<LeaveRequest> findByEmployee(int employeeId) throws SQLException {
        List<LeaveRequest> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     SELECT + "WHERE l.employee_id = ? ORDER BY l.requested_at DESC, l.leave_id DESC")) {
            ps.setInt(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    /**
     * How many of the employee's requests touch any of these dates. Always counts
     * APPROVED ones; also counts PENDING ones when includePending is true.
     */
    public int countOverlapping(int employeeId, LocalDate start, LocalDate end, boolean includePending)
            throws SQLException {
        String statuses = includePending ? "('PENDING','APPROVED')" : "('APPROVED')";
        String sql = "SELECT COUNT(*) FROM leave_request " +
                     "WHERE employee_id = ? AND status IN " + statuses + " " +
                     "AND start_date <= ? AND end_date >= ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ps.setDate(2, Date.valueOf(end));
            ps.setDate(3, Date.valueOf(start));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Creates a request. Staff requests are PENDING with no decider; an
     * administrator recording leave directly passes APPROVED plus their user id.
     */
    public int create(int employeeId, LeaveType type, LocalDate start, LocalDate end, String reason,
                      LeaveStatus status, Integer decidedByUserId, String adminNote) throws SQLException {
        String sql = "INSERT INTO leave_request (employee_id, leave_type, start_date, end_date, reason, " +
                     "status, decided_by, decided_at, admin_note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, employeeId);
            ps.setString(2, type.name());
            ps.setDate(3, Date.valueOf(start));
            ps.setDate(4, Date.valueOf(end));
            ps.setString(5, reason);
            ps.setString(6, status.name());
            ps.setObject(7, decidedByUserId, Types.INTEGER);
            if (status == LeaveStatus.PENDING) {
                ps.setNull(8, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            }
            ps.setString(9, adminNote);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    /** Approve or reject a request that is still PENDING. Returns false if it was no longer pending. */
    public boolean decide(int leaveId, LeaveStatus newStatus, int decidedByUserId, String adminNote)
            throws SQLException {
        String sql = "UPDATE leave_request SET status = ?, decided_by = ?, decided_at = ?, admin_note = ? " +
                     "WHERE leave_id = ? AND status = 'PENDING'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus.name());
            ps.setInt(2, decidedByUserId);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(4, adminNote);
            ps.setInt(5, leaveId);
            return ps.executeUpdate() == 1;
        }
    }

    /** A staff member withdraws their OWN request, only while it is still PENDING. */
    public boolean cancel(int leaveId, int employeeId) throws SQLException {
        String sql = "UPDATE leave_request SET status = 'CANCELLED' " +
                     "WHERE leave_id = ? AND employee_id = ? AND status = 'PENDING'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, leaveId);
            ps.setInt(2, employeeId);
            return ps.executeUpdate() == 1;
        }
    }

    private LeaveRequest mapRow(ResultSet rs) throws SQLException {
        Timestamp requested = rs.getTimestamp("requested_at");
        Timestamp decided = rs.getTimestamp("decided_at");
        return new LeaveRequest(
                rs.getInt("leave_id"),
                rs.getInt("employee_id"),
                rs.getString("employee_name"),
                LeaveType.valueOf(rs.getString("leave_type")),
                rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date").toLocalDate(),
                rs.getString("reason"),
                LeaveStatus.valueOf(rs.getString("status")),
                requested == null ? null : requested.toLocalDateTime(),
                rs.getString("decider_name"),
                decided == null ? null : decided.toLocalDateTime(),
                rs.getString("admin_note"));
    }
}
