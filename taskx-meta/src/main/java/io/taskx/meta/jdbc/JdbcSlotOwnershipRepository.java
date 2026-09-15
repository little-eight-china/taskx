package io.taskx.meta.jdbc;

import io.taskx.core.store.ExecutorRegistry;
import io.taskx.core.store.SlotOccupiedException;
import io.taskx.core.store.SlotOwnershipRepository;
import io.taskx.meta.MetaException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

public final class JdbcSlotOwnershipRepository implements SlotOwnershipRepository, ExecutorRegistry {

    private final DataSource dataSource;

    public JdbcSlotOwnershipRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Optional<String> findOwner(int slotNo) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT executor_id FROM tx_slot_ownership WHERE slot_no = ?")) {
            statement.setInt(1, slotNo);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(rs.getString(1));
            }
        } catch (SQLException ex) {
            throw new MetaException("find owner slot " + slotNo, ex);
        }
    }

    @Override
    public void claim(int slotNo, String executorId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertExecutor(connection, executorId);
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT executor_id FROM tx_slot_ownership WHERE slot_no = ? FOR UPDATE")) {
                    select.setInt(1, slotNo);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) {
                            try (PreparedStatement insert = connection.prepareStatement(
                                    "INSERT INTO tx_slot_ownership (slot_no, executor_id) VALUES (?, ?)")) {
                                insert.setInt(1, slotNo);
                                insert.setString(2, executorId);
                                insert.executeUpdate();
                            }
                        } else {
                            String owner = rs.getString(1);
                            if (!executorId.equals(owner)) {
                                connection.rollback();
                                throw new SlotOccupiedException(slotNo, owner);
                            }
                        }
                    }
                }
                connection.commit();
            } catch (SlotOccupiedException occupied) {
                throw occupied;
            } catch (SQLException ex) {
                connection.rollback();
                throw new MetaException("claim slot " + slotNo, ex);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new MetaException("claim slot " + slotNo, ex);
        }
    }

    @Override
    public void heartbeat(String executorId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE tx_executor SET last_heartbeat_at = CURRENT_TIMESTAMP(3) WHERE executor_id = ?")) {
            statement.setString(1, executorId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new MetaException("heartbeat " + executorId, ex);
        }
    }

    static void upsertExecutor(Connection connection, String executorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tx_executor (executor_id) VALUES (?) ON DUPLICATE KEY UPDATE executor_id = executor_id")) {
            statement.setString(1, executorId);
            statement.executeUpdate();
        }
    }
}
