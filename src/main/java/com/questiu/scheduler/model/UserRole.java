package com.questiu.scheduler.model;

/**
 * Matches user_account.role in the schema.
 * STAFF was added in FYP2 (migration 007): a staff login is linked to one
 * employee and will see the staff screens, not the admin ones.
 */
public enum UserRole {
    MANAGER, ADMIN, STAFF
}
