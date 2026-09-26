package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.EmploymentType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class EmployeeDao {

    private static final String SELECT_COLUMNS =
            "employee_id, full_name, role_id, hourly_rate, max_hours_week, is_active, " +
            "bank_account_number, employment_type";

    public List<Employee> findAllActive() throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM employee WHERE is_active = TRUE";
        return query(sql);
    }

    /** Includes inactive employees too - used by the Employees management/registration screen. */
    public List<Employee> findAll() throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM employee ORDER BY employee_id";
        return query(sql);
    }

    private List<Employee> query(String sql) throws SQLException {
        List<Employee> employees = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                employees.add(mapRow(rs));
            }
        }
        return employees;
    }

    /**
     * Registers a new employee (Section 3.7 - data-entry screen, added per
     * supervisor feedback) and returns the generated employee_id.
     */
    public int register(Employee emp) throws SQLException {
        String sql = "INSERT INTO employee (role_id, full_name, bank_account_number, employment_type, " +
                     "hourly_rate, max_hours_week, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, emp.getRoleId());
            ps.setString(2, emp.getFullName());
            ps.setString(3, emp.getBankAccountNumber());
            ps.setString(4, emp.getEmploymentType().name());
            ps.setBigDecimal(5, emp.getHourlyRate());
            ps.setBigDecimal(6, emp.getMaxWeeklyHours());
            ps.setBoolean(7, emp.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    private Employee mapRow(ResultSet rs) throws SQLException {
        return new Employee(
                rs.getInt("employee_id"),
                rs.getString("full_name"),
                rs.getInt("role_id"),
                rs.getBigDecimal("hourly_rate"),
                rs.getBigDecimal("max_hours_week"),
                rs.getBoolean("is_active"),
                rs.getString("bank_account_number"),
                EmploymentType.valueOf(rs.getString("employment_type"))
        );
    }
}
