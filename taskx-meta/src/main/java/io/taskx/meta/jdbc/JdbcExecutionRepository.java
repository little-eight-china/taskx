package io.taskx.meta.jdbc;

import io.taskx.core.domain.Execution;
import io.taskx.core.domain.ExecutionStatus;
import io.taskx.core.store.ExecutionRepository;
import io.taskx.meta.MetaException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcExecutionRepository implements ExecutionRepository {

    private final DataSource dataSource;

    public JdbcExecutionRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Optional<Execution> insertPending(Execution execution) {
        String sql = """
                INSERT INTO tx_execution (task_id, scheduled_fire_time, executor_id, status)
                VALUES (?, ?, ?, 'PENDING')
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, execution.taskId());
            statement.setLong(2, execution.scheduledFireTime());
            statement.setString(3, execution.executorId());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new MetaException("insert execution produced no id");
                }
                return Optional.of(execution.withId(keys.getLong(1)));
            }
        } catch (SQLException ex) {
            if (isDuplicate(ex)) {
                return Optional.empty();
            }
            throw new MetaException("insert execution", ex);
        }
    }

    @Override
    public boolean casStatus(long id, ExecutionStatus expected, ExecutionStatus next) {
        String sql = """
                UPDATE tx_execution
                   SET status = ?,
                       started_at = CASE WHEN ? = 'RUNNING' THEN CURRENT_TIMESTAMP(3) ELSE started_at END,
                       finished_at = CASE WHEN ? IN ('SUCCESS', 'FAILED', 'CANCELLED') THEN CURRENT_TIMESTAMP(3) ELSE finished_at END
                 WHERE id = ? AND status = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, next.name());
            statement.setString(2, next.name());
            statement.setString(3, next.name());
            statement.setLong(4, id);
            statement.setString(5, expected.name());
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new MetaException("cas execution " + id, ex);
        }
    }

    @Override
    public List<Execution> findPendingByExecutorId(String executorId) {
        String sql = """
                SELECT id, task_id, scheduled_fire_time, executor_id, status
                  FROM tx_execution
                 WHERE executor_id = ? AND status = 'PENDING'
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, executorId);
            try (ResultSet rs = statement.executeQuery()) {
                List<Execution> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new Execution(
                            rs.getLong("id"),
                            rs.getString("task_id"),
                            rs.getLong("scheduled_fire_time"),
                            rs.getString("executor_id"),
                            ExecutionStatus.valueOf(rs.getString("status"))
                    ));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new MetaException("find pending " + executorId, ex);
        }
    }

    private static boolean isDuplicate(SQLException ex) {
        return "23000".equals(ex.getSQLState()) || ex.getErrorCode() == 1062;
    }
}
