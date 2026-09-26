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
        String sql = "SELECT user_id, username, password_hash, full_name, role " +
                     "FROM user_account WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("user_id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("full_name"),
                            UserRole.valueOf(rs.getString("role"))
                    );
                }
            }
        }
        return null;
    }
}
