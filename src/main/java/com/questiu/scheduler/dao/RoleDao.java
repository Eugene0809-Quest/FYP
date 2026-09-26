package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RoleDao {

    public List<Role> findAll() throws SQLException {
        String sql = "SELECT role_id, role_name, description FROM role ORDER BY role_name";
        List<Role> roles = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                roles.add(new Role(
                        rs.getInt("role_id"),
                        rs.getString("role_name"),
                        rs.getString("description")
                ));
            }
        }
        return roles;
    }
}
