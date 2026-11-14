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
        return findOne("WHERE username = ?", ps -> ps.setString(1, username));
    }

    /** The login linked to an employee, or null if that employee has no login yet. */
    public User findByEmployeeId(int employeeId) throws SQLException {
        return findOne("WHERE employee_id = ?", ps -> ps.setInt(1, employeeId));
    }

    private interface ParamSetter {
        void set(PreparedStatement ps) throws SQLException;
    }

    private User findOne(String whereClause, ParamSetter setter) throws SQLException {
        String sql = "SELECT user_id, username, password_hash, full_name, role, employee_id " +
                     "FROM user_account " + whereClause;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setter.set(ps);
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

    /** employee_id -> username for every employee that has a login (used by the Employees table). */
    public java.util.Map<Integer, String> findUsernamesByEmployeeId() throws SQLException {
        java.util.Map<Integer, String> result = new java.util.HashMap<>();
        String sql = "SELECT employee_id, username FROM user_account WHERE employee_id IS NOT NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getInt("employee_id"), rs.getString("username"));
            }
        }
        return result;
    }

    /** Stores a new (already hashed) password for a login. */
    public void updatePasswordHash(int userId, String newPasswordHash) throws SQLException {
        String sql = "UPDATE user_account SET password_hash = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    /** Creates a STAFF login for an employee who does not have one yet. */
    public void createStaffLogin(int employeeId, String fullName, String username, String passwordHash)
            throws SQLException {
        String sql = "INSERT INTO user_account (username, password_hash, full_name, role, employee_id) " +
                     "VALUES (?, ?, ?, 'STAFF', ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, fullName);
            ps.setInt(4, employeeId);
            ps.executeUpdate();
        }
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