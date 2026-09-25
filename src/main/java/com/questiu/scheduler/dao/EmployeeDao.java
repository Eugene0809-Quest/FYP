package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.Employee;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class EmployeeDao {

    public List<Employee> findAllActive() throws SQLException {
        String sql = "SELECT employee_id, full_name, role_id, hourly_rate, max_weekly_hours, active " +
                     "FROM employee WHERE active = TRUE";
        List<Employee> employees = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                employees.add(new Employee(
                        rs.getInt("employee_id"),
                        rs.getString("full_name"),
                        rs.getInt("role_id"),
                        rs.getBigDecimal("hourly_rate"),
                        rs.getBigDecimal("max_weekly_hours"),
                        rs.getBoolean("active")
                ));
            }
        }
        return employees;
    }
}
