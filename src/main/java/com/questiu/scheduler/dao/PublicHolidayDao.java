package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.PublicHoliday;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads/writes the public_holiday table (supervisor item #4, the
 * public-holiday payroll premium). Loaded once per roster generation and
 * matched against each shift's actual calendar date (Shift.actualDate) to
 * decide which hours PayrollCalculator pays at the 2x holiday rate.
 */
public class PublicHolidayDao {

    public List<PublicHoliday> findAll() throws SQLException {
        String sql = "SELECT holiday_id, holiday_date, description FROM public_holiday ORDER BY holiday_date";
        List<PublicHoliday> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new PublicHoliday(
                        rs.getInt("holiday_id"),
                        rs.getDate("holiday_date").toLocalDate(),
                        rs.getString("description")
                ));
            }
        }
        return result;
    }

    /** Just the dates, as a Set for O(1) lookup from PayrollCalculator. */
    public Set<LocalDate> findAllDates() throws SQLException {
        Set<LocalDate> dates = new HashSet<>();
        for (PublicHoliday h : findAll()) {
            dates.add(h.getDate());
        }
        return dates;
    }

    public void add(LocalDate date, String description) throws SQLException {
        String sql = "INSERT INTO public_holiday (holiday_date, description) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            ps.setString(2, description);
            ps.executeUpdate();
        }
    }

    public void delete(int holidayId) throws SQLException {
        String sql = "DELETE FROM public_holiday WHERE holiday_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, holidayId);
            ps.executeUpdate();
        }
    }
}
