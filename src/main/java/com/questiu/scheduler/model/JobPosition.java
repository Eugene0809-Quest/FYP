package com.questiu.scheduler.model;

/**
 * A job title inside a department (job_position table, migration 008),
 * e.g. Cashier or Supervisor in Front-of-House, Head Chef in Kitchen.
 * toString() returns just the name so it displays cleanly in a ComboBox.
 */
public class JobPosition {
    private final int jobPositionId;
    private final int roleId;
    private final String positionName;
    private final int rankLevel;

    public JobPosition(int jobPositionId, int roleId, String positionName, int rankLevel) {
        this.jobPositionId = jobPositionId;
        this.roleId = roleId;
        this.positionName = positionName;
        this.rankLevel = rankLevel;
    }

    public int getJobPositionId() { return jobPositionId; }
    public int getRoleId() { return roleId; }
    public String getPositionName() { return positionName; }
    public int getRankLevel() { return rankLevel; }

    @Override
    public String toString() { return positionName; }
}
