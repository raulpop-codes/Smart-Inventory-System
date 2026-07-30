package org.example.infrastructure.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TransactionManager {

    private static Connection currentConnection = null;

    // A list of actions temporarily blocked during the transaction lifecycle
    private static final List<Runnable> pendingNotifications = new ArrayList<>();

    public static Connection getConnection() throws SQLException {
        if (currentConnection == null || currentConnection.isClosed()) {
            return DatabaseConnection.getConnection();
        }
        return currentConnection;
    }

    public static boolean isTransactionActive() {
        try {
            return currentConnection != null && !currentConnection.isClosed();
        } catch (SQLException e) {
            return false; // If a communication error occurs, consider the transaction inactive
        }
    }

    public static void startTransaction() throws SQLException {
        if (isTransactionActive()) {
            throw new IllegalStateException("A transaction is already active!");
        }
        currentConnection = DatabaseConnection.getConnection();
        currentConnection.setAutoCommit(false);
        pendingNotifications.clear();
    }

    public static void queueNotification(Runnable notification) {
        if (isTransactionActive()) {
            pendingNotifications.add(notification);
        } else {
            notification.run();
        }
    }

    public static void commit() throws SQLException {
        if (currentConnection == null) {
            return;
        }

        try {
            if (!currentConnection.isClosed()) {
                currentConnection.commit();
            }
            for (Runnable notification : pendingNotifications) {
                notification.run();
            }
        } finally {
            closeConnection();
        }
    }

    public static void rollback() {
        if (!isTransactionActive()) {
            return;
        }
        try {
            currentConnection.rollback();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    private static void closeConnection() {
        if (currentConnection != null) {
            try {
                currentConnection.setAutoCommit(true);
                currentConnection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                currentConnection = null;
                pendingNotifications.clear();
            }
        }
    }
}