package com.questiu.scheduler.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Single point of JDBC connection configuration (Section 3.3 - Data layer). */
public class DatabaseConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/shift_scheduling?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASSWORD = "Ukin3933"; // local MySQL root password
    
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
