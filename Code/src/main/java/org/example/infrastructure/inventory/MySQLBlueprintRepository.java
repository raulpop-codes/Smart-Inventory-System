package org.example.infrastructure.inventory;

import lombok.AllArgsConstructor;
import org.example.infrastructure.database.TransactionManager;
import org.example.model.inventory.Blueprint;
import org.example.model.inventory.ResourceComponent;
import org.example.model.enums.CraftingState;
import org.example.model.enums.CraftingType;
import org.example.service.inventory.BlueprintRepository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
public class MySQLBlueprintRepository implements BlueprintRepository {

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
    public Optional<Blueprint> getBlueprint(String idOrName) {
        String sqlBlueprint = """
            SELECT
                b.id AS bp_id,
                b.name AS bp_name,
                b.result_item_id,
                b.credit_cost,
                c.name AS category_name,
                COALESCE(ub.is_crafted, 0) AS is_crafted,
                ub.crafting_finished_at
            FROM blueprints b
            LEFT JOIN items i ON b.result_item_id = i.id
            LEFT JOIN item_categories c ON i.category_id = c.id
            LEFT JOIN user_blueprints ub ON b.id = ub.blueprint_id AND ub.user_id = ?
            WHERE CAST(b.id AS CHAR) = ? OR LOWER(b.name) = LOWER(?)
            LIMIT 1
        """;

        return executeQuery(
                sqlBlueprint,
                stmt -> {
                    stmt.setLong(1, userId);
                    stmt.setString(2, idOrName);
                    stmt.setString(3, idOrName);
                },
                rs -> {
                    String bpId = rs.getString("bp_id");
                    String bpName = rs.getString("bp_name");
                    String resultItemId = rs.getString("result_item_id");
                    long creditCost = rs.getLong("credit_cost");

                    String catName = rs.getString("category_name");
                    CraftingType type = "WARFRAME".equalsIgnoreCase(catName)
                            ? CraftingType.WARFRAME
                            : CraftingType.COMPONENT_OR_WEAPON;

                    boolean isCrafted = rs.getBoolean("is_crafted");
                    Timestamp finishedTimestamp = rs.getTimestamp("crafting_finished_at");
                    LocalDateTime finishedAt = (finishedTimestamp != null) ? finishedTimestamp.toLocalDateTime() : null;

                    CraftingState state;
                    if (isCrafted) {
                        state = CraftingState.CRAFTED;
                    } else if (finishedAt != null) {
                        state = CraftingState.CRAFTING;
                    } else {
                        state = CraftingState.UNCRAFTED;
                    }

                    List<ResourceComponent> requirements = loadRequirementsForBlueprint(bpId);

                    return new Blueprint(bpId, bpName, resultItemId, creditCost, requirements, type, state, finishedAt);
                }
        );
    }

    @Override
    public List<Blueprint> getAllActiveBlueprints() {
        String sql = """
            SELECT b.id
            FROM blueprints b
            JOIN user_blueprints ub ON b.id = ub.blueprint_id
            WHERE ub.user_id = ? AND ub.quantity > 0 AND ub.is_crafted = 0
        """;

        List<String> blueprintIds = executeQueryList(sql,
                stmt -> stmt.setLong(1, userId),
                rs -> rs.getString("id"));

        List<Blueprint> activeBlueprints = new ArrayList<>();
        for (String id : blueprintIds) {
            this.getBlueprint(id).ifPresent(activeBlueprints::add);
        }
        return activeBlueprints;
    }

    @Override
    public List<Blueprint> getBlueprintsByCategory(String category) {
        String sql = """
            SELECT b.id
            FROM blueprints b
            JOIN items i ON b.result_item_id = i.id
            JOIN item_categories c ON i.category_id = c.id
            JOIN user_blueprints ub ON b.id = ub.blueprint_id
            WHERE ub.user_id = ?
              AND ub.quantity > 0
              AND ub.is_crafted = 0
              AND UPPER(c.name) = UPPER(?)
        """;

        List<String> blueprintIds = executeQueryList(
                sql,
                stmt -> {
                    stmt.setLong(1, userId);
                    stmt.setString(2, category);
                },
                rs -> rs.getString("id")
        );

        List<Blueprint> categoryBlueprints = new ArrayList<>();
        for (String id : blueprintIds) {
            this.getBlueprint(id).ifPresent(categoryBlueprints::add);
        }
        return categoryBlueprints;
    }

    @Override
    public void saveBlueprint(Blueprint blueprint) {
        String sql = """
            INSERT INTO user_blueprints (user_id, blueprint_id, quantity, is_crafted, crafting_finished_at)
            VALUES (?, ?, 1, ?, ?)
            ON DUPLICATE KEY UPDATE
                is_crafted = VALUES(is_crafted),
                crafting_finished_at = VALUES(crafting_finished_at)
        """;

        executeUpdate(sql, stmt -> {
            stmt.setLong(1, userId);
            stmt.setString(2, blueprint.getID());
            stmt.setBoolean(3, blueprint.isCrafted());

            if (blueprint.getCraftingFinishedAt() != null && !blueprint.isCrafted()) {
                stmt.setTimestamp(4, Timestamp.valueOf(blueprint.getCraftingFinishedAt()));
            } else {
                stmt.setNull(4, Types.TIMESTAMP);
            }
        });
    }

    private List<ResourceComponent> loadRequirementsForBlueprint(String blueprintId) {
        String sql = """
            SELECT br.required_item_id, i.name AS item_name, br.required_quantity
            FROM blueprint_requirements br
            JOIN items i ON br.required_item_id = i.id
            WHERE br.blueprint_id = ?
        """;

        return executeQueryList(
                sql,
                stmt -> stmt.setString(1, blueprintId),
                rs -> new ResourceComponent(
                        rs.getString("required_item_id"),
                        rs.getString("item_name"),
                        rs.getLong("required_quantity")
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