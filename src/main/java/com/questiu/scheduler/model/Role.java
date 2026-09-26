package com.questiu.scheduler.model;

/** A job role (Section 3.3) - shown as "Position" on the registration form. */
public class Role {
    private final int roleId;
    private final String roleName;
    private final String description;

    public Role(int roleId, String roleName, String description) {
        this.roleId = roleId;
        this.roleName = roleName;
        this.description = description;
    }

    public int getRoleId() { return roleId; }
    public String getRoleName() { return roleName; }
    public String getDescription() { return description; }

    /** So this displays as a plain role name when dropped straight into a JavaFX ComboBox. */
    @Override
    public String toString() { return roleName; }
}
