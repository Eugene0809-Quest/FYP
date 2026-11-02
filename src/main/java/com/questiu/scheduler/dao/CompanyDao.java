package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.Company;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalTime;

/** Reads the single company row (company table, migration 007). */
public class CompanyDao {

    /** Returns null if the company row has not been created yet. */
    public Company find() throws SQLException {
        String sql = "SELECT company_name, registration_no, business_type, address, contact_number, " +
                     "email, opening_time, closing_time, description FROM company WHERE company_id = 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            Time opening = rs.getTime("opening_time");
            Time closing = rs.getTime("closing_time");
            LocalTime openingTime = opening == null ? null : opening.toLocalTime();
            LocalTime closingTime = closing == null ? null : closing.toLocalTime();
            return new Company(
                    rs.getString("company_name"),
                    rs.getString("registration_no"),
                    rs.getString("business_type"),
                    rs.getString("address"),
                    rs.getString("contact_number"),
                    rs.getString("email"),
                    openingTime,
                    closingTime,
                    rs.getString("description"));
        }
    }
}
