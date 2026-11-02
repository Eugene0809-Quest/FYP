package com.questiu.scheduler.model;

/**
 * A logged-in user (Section 3.7 - login screen).
 * employeeId is null for ADMIN and MANAGER accounts and is the linked
 * employee for a STAFF account (user_account.employee_id, migration 007).
 */
public class User {
    private final int userId;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final UserRole role;
    private final Integer employeeId;

    public User(int userId, String username, String passwordHash, String fullName,
                UserRole role, Integer employeeId) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.employeeId = employeeId;
    }

    /** Kept so existing code that builds an admin/manager User still compiles. */
    public User(int userId, String username, String passwordHash, String fullName, UserRole role) {
        this(userId, username, passwordHash, fullName, role, null);
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public UserRole getRole() { return role; }

    /** The linked employee for a STAFF account; null for ADMIN and MANAGER. */
    public Integer getEmployeeId() { return employeeId; }

    public boolean isStaff() { return role == UserRole.STAFF; }
    public boolean isAdminOrManager() { return role == UserRole.ADMIN || role == UserRole.MANAGER; }
}
