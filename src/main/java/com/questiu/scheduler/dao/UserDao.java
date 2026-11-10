package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.User;
import com.questiu.scheduler.model.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDao {

    /** Returns null if no matching username exists - caller compares the password hash. */
    public User findByUsername(String username) throws SQLException {
        String sql = "SELECT user_id, username, password_hash, full_name, role, employee_id " +
                     "FROM user_account WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int linkedEmployee = rs.getInt("employee_id");
                    Integer employeeId = rs.wasNull() ? null : linkedEmployee;
                    return new User(
                            rs.getInt("user_id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("full_name"),
                            UserRole.valueOf(rs.getString("role")),
                            employeeId
                    );
                }
            }
        }
        return null;
    }

    /** True if a login with this username already exists (used to give a friendly message before saving). */
    public boolean usernameExists(String username) throws SQLException {
        String sql = "SELECT 1 FROM user_account WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * True if this login may be used. ADMIN and MANAGER accounts have no linked
     * employee, so they are always allowed. A STAFF account is allowed only while
     * its linked employee is active (employee.is_active), so deactivating an
     * employee (e.g. when they leave) also blocks their login.
     */
    public boolean isLoginAllowed(User user) throws SQLException {
        if (user.getEmployeeId() == null) {
            return true;
        }
        String sql = "SELECT is_active FROM employee WHERE employee_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.getEmployeeId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("is_active");
            }
        }
    }
}