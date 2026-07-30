package org.example.infrastructure.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String HOST = requireEnv("DB_HOST");
    private static final String PORT = requireEnv("DB_PORT");
    private static final String DB_NAME = requireEnv("DB_NAME");

    private static final String USER = requireEnv("DB_USER");
    private static final String PASSWORD = requireEnv("DB_ROOT_PASSWORD");

    private static final String URL = String.format(
            "jdbc:mysql://%s:%s/%s?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC",
            HOST, PORT, DB_NAME
    );

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Environment variable " + name + " is not set!");
        }
        return value;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}