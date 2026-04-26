package com.bustrack.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DBConnection — Singleton utility to manage MySQL connections.
 * Update DB_URL, USER, PASSWORD to match your MySQL Workbench settings.
 */
public class DBConnection {

    // ---- CONFIGURE THESE ----
	
    private static final String DB_URL  = "jdbc:mysql://shortline.proxy.rlwy.net:25532/railway?useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USER    = "root";         // Your MySQL username
    private static final String PASSWORD = "wkdPlLvzmtokfMVQPovSnbjiqKmQkzAV"; // Your MySQL password
    // -------------------------

    private static Connection connection = null;

    /**
     * Returns a singleton MySQL connection.
     * Reconnects automatically if connection is closed.
     */
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                // Load MySQL JDBC driver
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
                System.out.println("[DB] Connected to MySQL successfully.");
            } catch (ClassNotFoundException e) {
                System.err.println("[DB] MySQL Driver not found: " + e.getMessage());
                throw new SQLException("JDBC Driver not found.");
            }
        }
        return connection;
    }
}
