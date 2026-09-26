package com.questiu.scheduler.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Single point of JDBC connection configuration (Section 3.3 - Data layer).
 * Now points at the 'smartshift' database (schema realignment).
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
    private static final String URL = "jdbc:mysql://localhost:3306/smartshift?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASSWORD = System.getenv().getOrDefault("SMARTSHIFT_DB_PASSWORD", "");

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
