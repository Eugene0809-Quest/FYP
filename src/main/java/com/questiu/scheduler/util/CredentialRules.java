package com.questiu.scheduler.util;

/**
 * The rules for usernames and passwords, in one place so every screen that
 * creates or changes a login (registration, "Manage Login", "Change Password")
 * gives the same answer and the same message.
 */
public final class CredentialRules {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private CredentialRules() {}

    /** Returns an error message, or null if the username is acceptable (expected trimmed and lower case). */
    public static String validateUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "Enter a username for the staff login.";
        }
        if (!username.matches("[a-z0-9._]{3,30}")) {
            return "Username must be 3-30 characters: lowercase letters, digits, dot or underscore.";
        }
        return null;
    }

    /** Returns an error message, or null if the new password is acceptable. */
    public static String validatePassword(String username, String password, String confirm) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return "The password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        }
        if (username != null && password.equalsIgnoreCase(username)) {
            return "The password must not be the same as the username.";
        }
        if (!password.equals(confirm)) {
            return "The two passwords do not match.";
        }
        return null;
    }
}
