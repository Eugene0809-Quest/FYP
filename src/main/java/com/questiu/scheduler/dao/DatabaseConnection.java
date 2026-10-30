package com.questiu.scheduler.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Single point of JDBC connection configuration (Section 3.3 - Data layer).
 * Points at the 'smartshift' database.
 *
 * allowPublicKeyRetrieval=true was added after hitting "Public Key Retrieval
 * is not allowed" on login: MySQL 8's default caching_sha2_password auth
 * plugin needs to fetch the server's RSA public key to hash the password,
 * and the JDBC driver refuses to do that over an unencrypted connection
 * (useSSL=false) unless this flag is explicitly set. This is safe for a
 * local-only prototype talking to localhost; it would NOT be appropriate
 * for a real deployment over an untrusted network without SSL.
 *
 * SECURITY NOTE: the password is read from the SMARTSHIFT_DB_PASSWORD
 * environment variable instead of being hardcoded, since this repo is
 * public on GitHub. Set it before running, e.g.:
 *   Windows (cmd):        set SMARTSHIFT_DB_PASSWORD=your_password
 *   Windows (PowerShell): $env:SMARTSHIFT_DB_PASSWORD="your_password"
 *   macOS/Linux:          export SMARTSHIFT_DB_PASSWORD=your_password
 * Leave it unset if your local MySQL root password is blank.
 *
 * If a real password was committed to git history before this change,
 * rotate that MySQL password - deleting it from the latest commit does
 * NOT remove it from earlier commits in the repo's history.
 */
public class DatabaseConnection {
    private static final String URL =
            "jdbc:mysql://localhost:3306/smartshift?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASSWORD = System.getenv().getOrDefault("SMARTSHIFT_DB_PASSWORD", "");

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
