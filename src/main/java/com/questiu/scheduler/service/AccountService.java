package com.questiu.scheduler.service;

import com.questiu.scheduler.dao.UserDao;
import com.questiu.scheduler.model.User;
import com.questiu.scheduler.util.CredentialRules;
import com.questiu.scheduler.util.PasswordHasher;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * Everything about changing logins, kept out of the screens so it can be
 * tested on its own. Each method returns an error message to show the user,
 * or null when it worked.
 */
public class AccountService {

    private final UserDao users = new UserDao();

    /**
     * A signed-in user changes their OWN password. The current password must be
     * right. The login is read fresh from the database, so a second change in the
     * same session still checks against the password saved by the first.
     */
    public String changeOwnPassword(String username, String currentPassword,
                                    String newPassword, String confirm) throws SQLException {
        User user = users.findByUsername(username);
        if (user == null) {
            return "Your account could not be found.";
        }
        if (!PasswordHasher.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            return "The current password is not correct.";
        }
        String rule = CredentialRules.validatePassword(user.getUsername(), newPassword, confirm);
        if (rule != null) {
            return rule;
        }
        if (newPassword.equals(currentPassword)) {
            return "The new password must be different from the current one.";
        }
        users.updatePasswordHash(user.getUserId(), PasswordHasher.hash(newPassword));
        return null;
    }

    /** An admin sets a new password for an employee's existing login (e.g. a forgotten password). */
    public String resetPassword(int employeeId, String newPassword, String confirm) throws SQLException {
        User user = users.findByEmployeeId(employeeId);
        if (user == null) {
            return "This employee has no login yet. Create one instead.";
        }
        String rule = CredentialRules.validatePassword(user.getUsername(), newPassword, confirm);
        if (rule != null) {
            return rule;
        }
        users.updatePasswordHash(user.getUserId(), PasswordHasher.hash(newPassword));
        return null;
    }

    /** An admin creates a login for an employee who was registered without one. */
    public String createLogin(int employeeId, String fullName, String username,
                              String password, String confirm) throws SQLException {
        String cleanUsername = username == null ? "" : username.trim().toLowerCase();
        String rule = CredentialRules.validateUsername(cleanUsername);
        if (rule == null) {
            rule = CredentialRules.validatePassword(cleanUsername, password, confirm);
        }
        if (rule != null) {
            return rule;
        }
        if (users.findByEmployeeId(employeeId) != null) {
            return "This employee already has a login.";
        }
        if (users.usernameExists(cleanUsername)) {
            return "That username is already taken. Please choose another one.";
        }
        try {
            users.createStaffLogin(employeeId, fullName, cleanUsername, PasswordHasher.hash(password));
        } catch (SQLIntegrityConstraintViolationException dup) {
            // someone else took the name between the check and the save
            return "That username is already taken. Please choose another one.";
        }
        return null;
    }
}
