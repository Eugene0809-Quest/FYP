package com.questiu.scheduler.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads saved rosters back out of the database (schedule, schedule_assignment,
 * shift_definition) and publishes them (FYP2). A saved schedule is DRAFT or
 * GENERATED until an administrator approves it; only APPROVED schedules are
 * visible to staff.
 *
 * shift_definition is a weekly template, so the calendar date of a shift is
 * worked out from the schedule's Monday (week_start_date) plus the shift's
 * day of the week.
 */
public class ScheduleViewDao {

    /** One saved schedule, as listed for the administrator. */
    public static class ScheduleSummary {
        private final int scheduleId;
        private final LocalDate weekStart;
        private final String status;
        private final LocalDateTime savedAt;
        private final String savedBy;
        private final int assignmentCount;

        public ScheduleSummary(int scheduleId, LocalDate weekStart, String status,
                               LocalDateTime savedAt, String savedBy, int assignmentCount) {
            this.scheduleId = scheduleId;
            this.weekStart = weekStart;
            this.status = status;
            this.savedAt = savedAt;
            this.savedBy = savedBy;
            this.assignmentCount = assignmentCount;
        }

        public int getScheduleId() { return scheduleId; }
        public LocalDate getWeekStart() { return weekStart; }
        public String getStatus() { return status; }
        public LocalDateTime getSavedAt() { return savedAt; }
        public String getSavedBy() { return savedBy; }
        public int getAssignmentCount() { return assignmentCount; }
    }

    /** One employee working one shift on one calendar date. */
    public static class ShiftRow {
        private final int scheduleId;
        private final LocalDate weekStart;
        private final LocalDate date;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final String department;
        private final String employeeName;
        private final double hours;

        public ShiftRow(int scheduleId, LocalDate weekStart, LocalDate date, LocalTime startTime,
                        LocalTime endTime, String department, String employeeName, double hours) {
            this.scheduleId = scheduleId;
            this.weekStart = weekStart;
            this.date = date;
            this.startTime = startTime;
            this.endTime = endTime;
            this.department = department;
            this.employeeName = employeeName;
            this.hours = hours;
        }

        public int getScheduleId() { return scheduleId; }
        public LocalDate getWeekStart() { return weekStart; }
        public LocalDate getDate() { return date; }
        public LocalTime getStartTime() { return startTime; }
        public LocalTime getEndTime() { return endTime; }
        public String getDepartment() { return department; }
        public String getEmployeeName() { return employeeName; }
        public double getHours() { return hours; }
    }

    private static final String SHIFT_SELECT =
            "SELECT s.schedule_id, s.week_start_date, sd.day_of_week, sd.start_time, sd.end_time, " +
            "r.role_name, e.full_name, a.assigned_hours " +
            "FROM schedule_assignment a " +
            "JOIN schedule s ON s.schedule_id = a.schedule_id " +
            "JOIN shift_definition sd ON sd.shift_id = a.shift_id " +
            "JOIN role r ON r.role_id = sd.role_id " +
            "JOIN employee e ON e.employee_id = a.employee_id ";

    private static final String SHIFT_ORDER =
            "ORDER BY s.week_start_date, FIELD(sd.day_of_week,'MON','TUE','WED','THU','FRI','SAT','SUN'), " +
            "sd.start_time, r.role_name, e.full_name";

    /** Every saved schedule, newest week first. */
    public List<ScheduleSummary> findSchedules() throws SQLException {
        String sql = "SELECT s.schedule_id, s.week_start_date, s.status, s.generated_at, " +
                     "u.full_name AS saved_by, " +
                     "(SELECT COUNT(*) FROM schedule_assignment a WHERE a.schedule_id = s.schedule_id) AS assignment_count " +
                     "FROM schedule s LEFT JOIN user_account u ON u.user_id = s.generated_by " +
                     "ORDER BY s.week_start_date DESC, s.schedule_id DESC";
        List<ScheduleSummary> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                java.sql.Timestamp saved = rs.getTimestamp("generated_at");
                result.add(new ScheduleSummary(
                        rs.getInt("schedule_id"),
                        rs.getDate("week_start_date").toLocalDate(),
                        rs.getString("status"),
                        saved == null ? null : saved.toLocalDateTime(),
                        rs.getString("saved_by"),
                        rs.getInt("assignment_count")));
            }
        }
        return result;
    }

    /** Everyone's shifts in one saved schedule - the administrator's timetable. */
    public List<ShiftRow> findAssignments(int scheduleId) throws SQLException {
        return queryShifts(SHIFT_SELECT + "WHERE a.schedule_id = ? " + SHIFT_ORDER, scheduleId);
    }

    /** One employee's shifts across all PUBLISHED (approved) schedules - the staff timetable. */
    public List<ShiftRow> findPublishedShifts(int employeeId) throws SQLException {
        return queryShifts(SHIFT_SELECT + "WHERE s.status = 'APPROVED' AND a.employee_id = ? " + SHIFT_ORDER,
                employeeId);
    }

    private List<ShiftRow> queryShifts(String sql, int id) throws SQLException {
        List<ShiftRow> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate weekStart = rs.getDate("week_start_date").toLocalDate();
                    int dayNo = DayOfWeekMapper.toInt(rs.getString("day_of_week"));
                    result.add(new ShiftRow(
                            rs.getInt("schedule_id"),
                            weekStart,
                            weekStart.plusDays(dayNo - 1),
                            rs.getTime("start_time").toLocalTime(),
                            rs.getTime("end_time").toLocalTime(),
                            rs.getString("role_name"),
                            rs.getString("full_name"),
                            rs.getBigDecimal("assigned_hours").doubleValue()));
                }
            }
        }
        return result;
    }

    /**
     * Publishes a schedule to staff. Only one schedule per week can be
     * published, so any other APPROVED schedule for the same week goes back to
     * DRAFT in the same transaction. A schedule with no assignments, or one
     * that is already APPROVED, is refused.
     *
     * @return the number of earlier schedules that were replaced (0 or more),
     *         or -1 if this schedule could not be published
     */
    public int approve(int scheduleId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                LocalDate weekStart;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT week_start_date FROM schedule WHERE schedule_id = ?")) {
                    ps.setInt(1, scheduleId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return -1;
                        }
                        weekStart = rs.getDate(1).toLocalDate();
                    }
                }
                int replaced;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE schedule SET status = 'DRAFT' " +
                        "WHERE week_start_date = ? AND status = 'APPROVED' AND schedule_id <> ?")) {
                    ps.setDate(1, java.sql.Date.valueOf(weekStart));
                    ps.setInt(2, scheduleId);
                    replaced = ps.executeUpdate();
                }
                int approved;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE schedule SET status = 'APPROVED' " +
                        "WHERE schedule_id = ? AND status IN ('DRAFT','GENERATED') " +
                        "AND EXISTS (SELECT 1 FROM schedule_assignment sa WHERE sa.schedule_id = ?)")) {
                    ps.setInt(1, scheduleId);
                    ps.setInt(2, scheduleId);
                    approved = ps.executeUpdate();
                }
                if (approved != 1) {
                    conn.rollback();
                    return -1;
                }
                conn.commit();
                return replaced;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /** Withdraws a published schedule from staff (back to DRAFT). Returns false if it was not published. */
    public boolean unpublish(int scheduleId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE schedule SET status = 'DRAFT' WHERE schedule_id = ? AND status = 'APPROVED'")) {
            ps.setInt(1, scheduleId);
            return ps.executeUpdate() == 1;
        }
    }
}
