package org.example.infrastructure.inventory;

import lombok.AllArgsConstructor;
import org.example.infrastructure.database.TransactionManager;
import org.example.model.inventory.ResourceComponent;
import org.example.service.inventory.InventoryRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
public class MySQLInventoryRepository implements InventoryRepository {

    private final long userId;

    // --- HELPER INTERFACES AND METHODS ---

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement stmt) throws SQLException;
    }

    @FunctionalInterface
    private interface ResultSetMapper<T> {
        T map(ResultSet rs) throws Exception;
    }

    private <T> Optional<T> executeQuery(String sql, StatementBinder binder, ResultSetMapper<T> mapper) {
        List<T> results = executeQueryList(sql, binder, mapper);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    private <T> List<T> executeQueryList(String sql, StatementBinder binder, ResultSetMapper<T> mapper) {
        List<T> list = new ArrayList<>();
        Connection conn = null;
        try {
            conn = TransactionManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (binder != null) binder.bind(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapper.map(rs));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Eroare la executarea interogării SQL: " + e.getMessage(), e);
        } finally {
            closeNonTransactionalConnection(conn);
        }
        return list;
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
            throw new RuntimeException("Eroare la actualizarea bazei de date: " + e.getMessage(), e);
        } finally {
            closeNonTransactionalConnection(conn);
        }
    }

    // --- IMPLEMENTAREA METODELOR REPOSITORY ---

    @Override
    public Optional<ResourceComponent> getComponent(String idOrName) {
        String sql = """
            SELECT
                i.id AS item_id,
                i.name AS item_name,
                COALESCE(ui.quantity, 0) AS stock
            FROM items i
            LEFT JOIN user_items ui ON i.id = ui.item_id AND ui.user_id = ?
            WHERE CAST(i.id AS CHAR) = ? OR LOWER(i.name) = LOWER(?)
            LIMIT 1
        """;

        return executeQuery(
                sql,
                stmt -> {
                    stmt.setLong(1, userId);
                    stmt.setString(2, idOrName);
                    stmt.setString(3, idOrName);
                },
                rs -> new ResourceComponent(
                        rs.getString("item_id"),
                        rs.getString("item_name"),
                        rs.getLong("stock")
                )
        );
    }

    @Override
    public void saveComponent(ResourceComponent comp) {
        String sql = """
            INSERT INTO user_items (user_id, item_id, quantity)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE quantity = VALUES(quantity)
        """;

        executeUpdate(sql, stmt -> {
            stmt.setLong(1, userId);
            stmt.setString(2, comp.getID());
            stmt.setLong(3, comp.getQuantity());
        });
    }

    @Override
    public List<ResourceComponent> getAllComponents() {
        String sql = """
            SELECT
                i.id AS item_id,
                i.name AS item_name,
                COALESCE(ui.quantity, 0) AS stock
            FROM items i
            LEFT JOIN user_items ui ON i.id = ui.item_id AND ui.user_id = ?
            ORDER BY i.name ASC
        """;

        return executeQueryList(
                sql,
                stmt -> stmt.setLong(1, userId),
                rs -> new ResourceComponent(
                        rs.getString("item_id"),
                        rs.getString("item_name"),
                        rs.getLong("stock")
                )
        );
    }

    @Override
    public List<ResourceComponent> getComponentsByCategory(String category) {
        String sql = """
            SELECT
                i.id AS item_id,
                i.name AS item_name,
                COALESCE(ui.quantity, 0) AS stock
            FROM items i
            JOIN item_categories c ON i.category_id = c.id
            LEFT JOIN user_items ui ON i.id = ui.item_id AND ui.user_id = ?
            WHERE LOWER(c.name) = LOWER(?)
            ORDER BY i.name ASC
        """;

        return executeQueryList(
                sql,
                stmt -> {
                    stmt.setLong(1, userId);
                    stmt.setString(2, category);
                },
                rs -> new ResourceComponent(
                        rs.getString("item_id"),
                        rs.getString("item_name"),
                        rs.getLong("stock")
                )
        );
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