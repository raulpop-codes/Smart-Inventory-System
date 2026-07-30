package org.example.infrastructure.mission;

import lombok.AllArgsConstructor;
import org.example.infrastructure.database.TransactionManager;
import org.example.model.enums.MissionState;
import org.example.model.inventory.ResourceComponent;
import org.example.model.mission.Mission;
import org.example.service.mission.MissionRepository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
public class MySQLMissionRepository implements MissionRepository {

    private final long userId;

    // --- INTERFACES AND HELPER METHODS ---

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
            throw new RuntimeException("Error executing SQL query: " + e.getMessage(), e);
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
            throw new RuntimeException("Error updating database: " + e.getMessage(), e);
        } finally {
            closeNonTransactionalConnection(conn);
        }
    }

    // --- REPOSITORY METHOD IMPLEMENTATIONS ---

    @Override
    public Optional<Mission> getMissionBySlot(int slotIndex) {
        String sql = """
            SELECT id, slot_index, name, location, duration_minutes, state, finished_at
            FROM user_missions
            WHERE user_id = ? AND slot_index = ?
            LIMIT 1
        """;

        return executeQuery(
                sql,
                stmt -> {
                    stmt.setLong(1, userId);
                    stmt.setInt(2, slotIndex);
                },
                rs -> mapResultSetToMission(rs)
        );
    }

    @Override
    public List<Mission> getAllMissions() {
        String sql = """
            SELECT id, slot_index, name, location, duration_minutes, state, finished_at
            FROM user_missions
            WHERE user_id = ?
            ORDER BY slot_index ASC
        """;

        return executeQueryList(
                sql,
                stmt -> stmt.setLong(1, userId),
                rs -> mapResultSetToMission(rs)
        );
    }

    @Override
    public void saveMission(Mission mission) {
        String sql = """
            INSERT INTO user_missions (id, user_id, slot_index, name, location, duration_minutes, state, finished_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                state = VALUES(state),
                finished_at = VALUES(finished_at)
        """;

        executeUpdate(sql, stmt -> {
            stmt.setString(1, mission.getID());
            stmt.setLong(2, userId);
            stmt.setInt(3, mission.getSlotIndex());
            stmt.setString(4, mission.getName());
            stmt.setString(5, mission.getLocation());
            stmt.setInt(6, mission.getDurationMinutes());
            stmt.setString(7, mission.getState().name());

            if (mission.getFinishedAt() != null) {
                stmt.setTimestamp(8, Timestamp.valueOf(mission.getFinishedAt()));
            } else {
                stmt.setNull(8, Types.TIMESTAMP);
            }
        });

        // Save mission rewards into the relational table
        saveMissionRewards(mission);
    }

    @Override
    public void deleteMissionBySlot(int slotIndex) {
        String sql = "DELETE FROM user_missions WHERE user_id = ? AND slot_index = ?";
        executeUpdate(sql, stmt -> {
            stmt.setLong(1, userId);
            stmt.setInt(2, slotIndex);
        });
    }

    // --- PRIVATE REWARD AND MAPPING METHODS ---

    private Mission mapResultSetToMission(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        int slotIndex = rs.getInt("slot_index");
        String name = rs.getString("name");
        String location = rs.getString("location");
        int durationMinutes = rs.getInt("duration_minutes");

        MissionState state = MissionState.valueOf(rs.getString("state"));

        Timestamp finishedTs = rs.getTimestamp("finished_at");
        LocalDateTime finishedAt = (finishedTs != null) ? finishedTs.toLocalDateTime() : null;

        List<ResourceComponent> rewards = loadRewardsForMission(id);

        return new Mission(id, slotIndex, name, location, durationMinutes, rewards, state, finishedAt);
    }

    private List<ResourceComponent> loadRewardsForMission(String missionId) {
        String sql = """
            SELECT mr.item_id, i.name AS item_name, mr.quantity
            FROM mission_rewards mr
            JOIN items i ON mr.item_id = i.id
            WHERE mr.mission_id = ?
        """;

        return executeQueryList(
                sql,
                stmt -> stmt.setString(1, missionId),
                rs -> new ResourceComponent(
                        rs.getString("item_id"),
                        rs.getString("item_name"),
                        rs.getLong("quantity")
                )
        );
    }

    private void saveMissionRewards(Mission mission) {
        if (mission.getRewardResources() == null || mission.getRewardResources().isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO mission_rewards (mission_id, item_id, quantity)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE quantity = VALUES(quantity)
        """;

        for (ResourceComponent reward : mission.getRewardResources()) {
            executeUpdate(sql, stmt -> {
                stmt.setString(1, mission.getID());
                stmt.setString(2, reward.getID());
                stmt.setLong(3, reward.getQuantity());
            });
        }
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