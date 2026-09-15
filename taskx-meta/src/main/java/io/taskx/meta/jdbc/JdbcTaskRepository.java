package io.taskx.meta.jdbc;

import io.taskx.core.domain.Task;
import io.taskx.core.store.TaskRepository;
import io.taskx.meta.MetaException;
import io.taskx.meta.json.TriggerCodec;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcTaskRepository implements TaskRepository {

    private final DataSource dataSource;

    public JdbcTaskRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Optional<Task> findById(String taskId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT task_id, handler, payload, enabled, trigger_type, trigger_spec, workflow_json FROM tx_task WHERE task_id = ?")) {
            statement.setString(1, taskId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException ex) {
            throw new MetaException("find task " + taskId, ex);
        }
    }

    @Override
    public List<Task> findAll() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT task_id, handler, payload, enabled, trigger_type, trigger_spec, workflow_json FROM tx_task ORDER BY task_id");
             ResultSet rs = statement.executeQuery()) {
            List<Task> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(map(rs));
            }
            return List.copyOf(rows);
        } catch (SQLException ex) {
            throw new MetaException("list tasks", ex);
        }
    }

    @Override
    public void save(Task task) {
        try (Connection connection = dataSource.getConnection()) {
            save(connection, task);
        } catch (SQLException ex) {
            throw new MetaException("save task " + task.id(), ex);
        }
    }

    public void save(Connection connection, Task task) throws SQLException {
        String sql = """
                INSERT INTO tx_task (task_id, handler, payload, enabled, trigger_type, trigger_spec, workflow_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  handler = VALUES(handler),
                  payload = VALUES(payload),
                  enabled = VALUES(enabled),
                  trigger_type = VALUES(trigger_type),
                  trigger_spec = VALUES(trigger_spec),
                  workflow_json = VALUES(workflow_json)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, task.id());
            statement.setString(2, task.handler());
            statement.setString(3, task.payload());
            statement.setInt(4, task.enabled() ? 1 : 0);
            statement.setString(5, task.trigger().type().name());
            statement.setString(6, TriggerCodec.toSpecJson(task.trigger()));
            statement.setString(7, task.workflowJson());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete(String taskId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM tx_task WHERE task_id = ?")) {
            statement.setString(1, taskId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new MetaException("delete task " + taskId, ex);
        }
    }

    public void delete(Connection connection, String taskId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM tx_task WHERE task_id = ?")) {
            statement.setString(1, taskId);
            statement.executeUpdate();
        }
    }

    public static Task map(ResultSet rs) throws SQLException {
        return new Task(
                rs.getString("task_id"),
                rs.getString("handler"),
                rs.getString("payload"),
                rs.getInt("enabled") != 0,
                TriggerCodec.from(rs.getString("trigger_type"), rs.getString("trigger_spec")),
                rs.getString("workflow_json")
        );
    }
}
