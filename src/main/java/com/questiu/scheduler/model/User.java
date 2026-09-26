package com.questiu.scheduler.model;

/** A logged-in manager/admin (Section 3.7 - login screen). */
public class User {
    private final int userId;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final UserRole role;

    public User(int userId, String username, String passwordHash, String fullName, UserRole role) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public UserRole getRole() { return role; }
}
