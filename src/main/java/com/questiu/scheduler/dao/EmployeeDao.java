package com.questiu.scheduler.dao;

import com.questiu.scheduler.model.Employee;
import com.questiu.scheduler.model.EmploymentType;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class EmployeeDao {

    private static final String SELECT_COLUMNS =
            "employee_id, full_name, role_id, hourly_rate, max_hours_week, is_active, " +
            "bank_account_number, employment_type, " +
            "job_position_id, ic_number, epf_number, socso_number, eis_number, date_joined";

    public List<Employee> findAllActive() throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM employee WHERE is_active = TRUE";
        return query(sql);
    }

    /** Includes inactive employees too - used by the Employees management/registration screen. */
    public List<Employee> findAll() throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM employee ORDER BY employee_id";
        return query(sql);
    }

    /** One employee by id, or null if there is none - used by the staff "My Profile" screen. */
    public Employee findById(int employeeId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM employee WHERE employee_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
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
     * FYP2: also saves the job position and the IC / EPF / SOCSO / EIS
     * numbers and joining date. The database refuses a job position that
     * belongs to a different department than the employee's role, and a
     * duplicate IC number.
     */
    public int register(Employee emp) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return insertEmployee(conn, emp);
        }
    }

    /**
     * Registers the employee AND creates their STAFF login in ONE transaction
     * (FYP2, lecturer feedback: registering a staff member includes their login).
     * If anything fails - a duplicate username, a duplicate IC number, a position
     * from the wrong department - nothing is saved: you never end up with an
     * employee who has no login, or a login that points at no employee.
     *
     * @param passwordHash the already-hashed password (see PasswordHasher), never the plain text
     * @return the new employee_id
     */
    public int registerWithLogin(Employee emp, String username, String passwordHash) throws SQLException {
        String loginSql = "INSERT INTO user_account (username, password_hash, full_name, role, employee_id) " +
                          "VALUES (?, ?, ?, 'STAFF', ?)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int employeeId = insertEmployee(conn, emp);
                if (employeeId <= 0) {
                    throw new SQLException("The employee was saved but no employee_id was returned.");
                }
                try (PreparedStatement ps = conn.prepareStatement(loginSql)) {
                    ps.setString(1, username);
                    ps.setString(2, passwordHash);
                    ps.setString(3, emp.getFullName());
                    ps.setInt(4, employeeId);
                    ps.executeUpdate();
                }
                conn.commit();
                return employeeId;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private int insertEmployee(Connection conn, Employee emp) throws SQLException {
        String sql = "INSERT INTO employee (role_id, job_position_id, full_name, ic_number, " +
                     "bank_account_number, epf_number, socso_number, eis_number, employment_type, " +
                     "date_joined, hourly_rate, max_hours_week, is_active) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, emp.getRoleId());
            ps.setObject(2, emp.getJobPositionId(), Types.INTEGER);
            ps.setString(3, emp.getFullName());
            ps.setString(4, emp.getIcNumber());
            ps.setString(5, emp.getBankAccountNumber());
            ps.setString(6, emp.getEpfNumber());
            ps.setString(7, emp.getSocsoNumber());
            ps.setString(8, emp.getEisNumber());
            ps.setString(9, emp.getEmploymentType().name());
            if (emp.getDateJoined() == null) {
                ps.setNull(10, Types.DATE);
            } else {
                ps.setDate(10, Date.valueOf(emp.getDateJoined()));
            }
            ps.setBigDecimal(11, emp.getHourlyRate());
            ps.setBigDecimal(12, emp.getMaxWeeklyHours());
            ps.setBoolean(13, emp.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    private Employee mapRow(ResultSet rs) throws SQLException {
        int positionValue = rs.getInt("job_position_id");
        Integer jobPositionId = rs.wasNull() ? null : positionValue;
        Date joined = rs.getDate("date_joined");
        LocalDate dateJoined = joined == null ? null : joined.toLocalDate();
        return new Employee(
                rs.getInt("employee_id"),
                rs.getString("full_name"),
                rs.getInt("role_id"),
                rs.getBigDecimal("hourly_rate"),
                rs.getBigDecimal("max_hours_week"),
                rs.getBoolean("is_active"),
                rs.getString("bank_account_number"),
                EmploymentType.valueOf(rs.getString("employment_type")),
                jobPositionId,
                rs.getString("ic_number"),
                rs.getString("epf_number"),
                rs.getString("socso_number"),
                rs.getString("eis_number"),
                dateJoined
        );
    }
}
