package org.example.infrastructure.auth;

import org.example.infrastructure.database.TransactionManager;
import org.example.model.auth.User;
import org.example.service.auth.UserRepository;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.Optional;

public class MySQLAuthRepository implements UserRepository {

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement stmt) throws SQLException;
    }

    @FunctionalInterface
    private interface ResultSetMapper<T> {
        T map(ResultSet rs) throws Exception;
    }

    private <T> Optional<T> executeQuery(String sql, StatementBinder binder, ResultSetMapper<T> mapper) {
        Connection conn = null;
        try {
            conn = TransactionManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (binder != null) binder.bind(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(mapper.map(rs));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("DB Query Error: " + e.getMessage(), e);
        } finally {
            closeNonTransactionalConnection(conn);
        }
        return Optional.empty();
    }

    private int executeUpdate(String sql, StatementBinder binder) {
        Connection conn = null;
        try {
            conn = TransactionManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (binder != null) binder.bind(stmt);
                return stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB Update Error: " + e.getMessage(), e);
        } finally {
            closeNonTransactionalConnection(conn);
        }
    }

    // --- REPOSITORY IMPLEMENTATION ---

    @Override
    public void registerUser(User user) {
        String sql = "INSERT INTO users (username, password, email) VALUES (?, ?, ?)";

        String hashedPassword = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());

        executeUpdate(sql, stmt -> {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, hashedPassword);
            stmt.setString(3, user.getEmail());
        });
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return findUserByColumn("SELECT username, password, email FROM users WHERE username = ?", username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return findUserByColumn("SELECT username, password, email FROM users WHERE email = ?", email);
    }

    private Optional<User> findUserByColumn(String sql, String parameterValue) {
        return executeQuery(sql,
                stmt -> stmt.setString(1, parameterValue),
                rs -> User.fromDatabase(rs.getString("username"), rs.getString("password"), rs.getString("email"))
        );
    }

    @Override
    public Optional<Long> findUserIdByUsername(String username) {
        String sql = "SELECT id FROM users WHERE username = ?";
        return executeQuery(sql, stmt -> stmt.setString(1, username), rs -> rs.getLong("id"));
    }

    private void closeNonTransactionalConnection(Connection conn) {
        if (conn != null && !TransactionManager.isTransactionActive()) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}