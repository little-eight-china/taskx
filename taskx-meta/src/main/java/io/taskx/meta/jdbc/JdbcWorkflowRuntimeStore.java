package io.taskx.meta.jdbc;

import io.taskx.meta.MetaException;
import io.taskx.workflow.runtime.ClaimedActivation;
import io.taskx.workflow.runtime.OutboxEvent;
import io.taskx.workflow.runtime.WorkflowInstance;
import io.taskx.workflow.runtime.WorkflowInstanceStatus;
import io.taskx.workflow.store.WorkflowRuntimeStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcWorkflowRuntimeStore implements WorkflowRuntimeStore {

    private final DataSource dataSource;

    public JdbcWorkflowRuntimeStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public WorkflowInstance start(StartCommand command) {
        return tx("start workflow " + command.workflowId(), connection -> {
            String status = command.completesImmediately() ? "SUCCESS" : "RUNNING";
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO tx_workflow_instance
                        (instance_id, workflow_id, workflow_version, trigger_execution_id,
                         status, input_data, started_at, finished_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, command.instanceId());
                statement.setString(2, command.workflowId());
                statement.setInt(3, command.workflowVersion());
                statement.setLong(4, command.triggerExecutionId());
                statement.setString(5, status);
                statement.setString(6, command.inputJson());
                statement.setTimestamp(7, Timestamp.from(command.startedAt()));
                statement.setTimestamp(
                        8,
                        command.completesImmediately() ? Timestamp.from(command.startedAt()) : null
                );
                statement.executeUpdate();
            }
            if (command.firstActivation() != null) {
                insertActivationAndOutbox(connection, command.instanceId(), command.firstActivation());
            }
            if (command.completesImmediately()) {
                finishRootExecution(connection, command.instanceId(), "SUCCESS");
            }
            return new WorkflowInstance(
                    command.instanceId(),
                    command.workflowId(),
                    command.workflowVersion(),
                    command.triggerExecutionId(),
                    WorkflowInstanceStatus.valueOf(status),
                    command.inputJson(),
                    null,
                    command.startedAt(),
                    command.completesImmediately() ? command.startedAt() : null
            );
        });
    }

    @Override
    public Optional<ClaimedActivation> claim(
            String activationId,
            String workerGroup,
            int slotNo,
            String executorId,
            java.time.Instant leaseUntil,
            java.time.Instant now
    ) {
        return tx("claim workflow activation " + activationId, connection -> {
            String sql = """
                    SELECT a.activation_id, a.instance_id, a.node_id, a.activation_no,
                           a.current_attempt, a.timeout_seconds, a.input_data,
                           i.workflow_id, i.workflow_version, i.trigger_execution_id,
                           i.input_data AS workflow_input
                      FROM tx_workflow_node_activation a
                      JOIN tx_workflow_instance i ON i.instance_id = a.instance_id
                     WHERE a.activation_id = ?
                       AND a.worker_group = ?
                       AND a.slot_no = ?
                       AND a.status IN ('READY', 'RETRY_WAIT')
                       AND a.available_at <= ?
                       AND i.status = 'RUNNING'
                       FOR UPDATE
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, activationId);
                statement.setString(2, workerGroup);
                statement.setInt(3, slotNo);
                statement.setTimestamp(4, Timestamp.from(now));
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    int attempt = rs.getInt("current_attempt") + 1;
                    int timeoutSeconds = rs.getInt("timeout_seconds");
                    java.time.Instant nodeLeaseUntil =
                            now.plusSeconds(timeoutSeconds == 0 ? 0 : timeoutSeconds + 30L);
                    java.time.Instant effectiveLeaseUntil =
                            nodeLeaseUntil.isAfter(leaseUntil) ? nodeLeaseUntil : leaseUntil;
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE tx_workflow_node_activation
                               SET status = 'RUNNING',
                                   current_attempt = ?,
                                   lease_until = ?,
                                   started_at = COALESCE(started_at, ?)
                             WHERE activation_id = ?
                            """)) {
                        update.setInt(1, attempt);
                        update.setTimestamp(2, Timestamp.from(effectiveLeaseUntil));
                        update.setTimestamp(3, Timestamp.from(now));
                        update.setString(4, activationId);
                        update.executeUpdate();
                    }
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO tx_workflow_node_attempt
                                (activation_id, attempt_no, executor_id, status, started_at)
                            VALUES (?, ?, ?, 'RUNNING', ?)
                            """)) {
                        insert.setString(1, activationId);
                        insert.setInt(2, attempt);
                        insert.setString(3, executorId);
                        insert.setTimestamp(4, Timestamp.from(now));
                        insert.executeUpdate();
                    }
                    return Optional.of(new ClaimedActivation(
                            rs.getString("activation_id"),
                            rs.getString("instance_id"),
                            rs.getString("workflow_id"),
                            rs.getInt("workflow_version"),
                            rs.getLong("trigger_execution_id"),
                            rs.getString("node_id"),
                            rs.getInt("activation_no"),
                            attempt,
                            rs.getString("input_data"),
                            rs.getString("workflow_input")
                    ));
                }
            }
        });
    }

    @Override
    public void complete(CompleteCommand command) {
        tx("complete workflow activation " + command.activationId(), connection -> {
            String instanceId = lockRunningActivation(connection, command.activationId(), command.attempt());
            finishAttempt(connection, command.activationId(), command.attempt(), "SUCCESS", null, null, command.finishedAt());
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE tx_workflow_node_activation
                       SET status = 'SUCCESS', route_handle = ?, output_data = ?,
                           lease_until = NULL, finished_at = ?
                     WHERE activation_id = ? AND status = 'RUNNING' AND current_attempt = ?
                    """)) {
                update.setString(1, command.routeHandle());
                update.setString(2, command.outputJson());
                update.setTimestamp(3, Timestamp.from(command.finishedAt()));
                update.setString(4, command.activationId());
                update.setInt(5, command.attempt());
                requireOne(update.executeUpdate(), "activation is no longer RUNNING");
            }

            if (command.edgeId() != null && command.targetNodeId() != null) {
                boolean inserted = insertToken(
                        connection,
                        instanceId,
                        command.activationId(),
                        command.edgeId(),
                        command.targetNodeId()
                );
                if (inserted && command.nextActivation() != null) {
                    insertActivationAndOutbox(connection, instanceId, command.nextActivation());
                }
            }
            if (command.completesInstance()) {
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE tx_workflow_instance
                           SET status = 'SUCCESS', output_data = ?, finished_at = ?
                         WHERE instance_id = ? AND status = 'RUNNING'
                        """)) {
                    update.setString(1, command.outputJson());
                    update.setTimestamp(2, Timestamp.from(command.finishedAt()));
                    update.setString(3, instanceId);
                    requireOne(update.executeUpdate(), "workflow instance is no longer RUNNING");
                }
                finishRootExecution(connection, instanceId, "SUCCESS");
            }
            return null;
        });
    }

    @Override
    public void retry(RetryCommand command) {
        tx("retry workflow activation " + command.activationId(), connection -> {
            lockRunningActivation(connection, command.activationId(), command.attempt());
            finishAttempt(
                    connection,
                    command.activationId(),
                    command.attempt(),
                    "FAILED",
                    command.errorCode(),
                    command.errorMessage(),
                    command.failedAt()
            );
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE tx_workflow_node_activation
                       SET status = 'RETRY_WAIT', available_at = ?, lease_until = NULL
                     WHERE activation_id = ? AND status = 'RUNNING' AND current_attempt = ?
                    """)) {
                update.setTimestamp(1, Timestamp.from(command.availableAt()));
                update.setString(2, command.activationId());
                update.setInt(3, command.attempt());
                requireOne(update.executeUpdate(), "activation is no longer RUNNING");
            }
            insertOutbox(
                    connection,
                    command.activationId(),
                    command.workerGroup(),
                    command.slotNo(),
                    command.availableAt()
            );
            return null;
        });
    }

    @Override
    public void fail(FailCommand command) {
        tx("fail workflow activation " + command.activationId(), connection -> {
            String instanceId = lockRunningActivation(connection, command.activationId(), command.attempt());
            finishAttempt(
                    connection,
                    command.activationId(),
                    command.attempt(),
                    "FAILED",
                    command.errorCode(),
                    command.errorMessage(),
                    command.failedAt()
            );
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE tx_workflow_node_activation
                       SET status = 'FAILED', lease_until = NULL, finished_at = ?
                     WHERE activation_id = ? AND status = 'RUNNING' AND current_attempt = ?
                    """)) {
                update.setTimestamp(1, Timestamp.from(command.failedAt()));
                update.setString(2, command.activationId());
                update.setInt(3, command.attempt());
                requireOne(update.executeUpdate(), "activation is no longer RUNNING");
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE tx_workflow_instance
                       SET status = 'FAILED', finished_at = ?
                     WHERE instance_id = ? AND status = 'RUNNING'
                    """)) {
                update.setTimestamp(1, Timestamp.from(command.failedAt()));
                update.setString(2, instanceId);
                requireOne(update.executeUpdate(), "workflow instance is no longer RUNNING");
            }
            finishRootExecution(connection, instanceId, "FAILED");
            return null;
        });
    }

    @Override
    public List<Long> recoverExpiredLeases(java.time.Instant now, int limit) {
        return tx("recover expired workflow leases", connection -> {
            String sql = """
                    SELECT a.activation_id, a.instance_id, a.current_attempt, a.max_attempts,
                           a.retry_interval_sec, a.worker_group, a.slot_no, i.trigger_execution_id
                      FROM tx_workflow_node_activation a
                      JOIN tx_workflow_instance i ON i.instance_id = a.instance_id
                     WHERE a.status = 'RUNNING' AND a.lease_until < ?
                     ORDER BY a.lease_until
                     LIMIT ?
                       FOR UPDATE SKIP LOCKED
                    """;
            List<ExpiredActivation> expired = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setInt(2, Math.max(1, limit));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        expired.add(new ExpiredActivation(
                                rs.getString("activation_id"),
                                rs.getString("instance_id"),
                                rs.getInt("current_attempt"),
                                rs.getInt("max_attempts"),
                                rs.getInt("retry_interval_sec"),
                                rs.getString("worker_group"),
                                rs.getInt("slot_no"),
                                rs.getLong("trigger_execution_id")
                        ));
                    }
                }
            }
            List<Long> failedRootExecutions = new ArrayList<>();
            for (ExpiredActivation row : expired) {
                finishAttempt(
                        connection,
                        row.activationId(),
                        row.attempt(),
                        "TIMED_OUT",
                        "LEASE_EXPIRED",
                        "executor lease expired",
                        now
                );
                if (row.attempt() < row.maxAttempts()) {
                    java.time.Instant availableAt = now.plusSeconds(row.retryIntervalSeconds());
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE tx_workflow_node_activation
                               SET status = 'RETRY_WAIT', lease_until = NULL, available_at = ?
                             WHERE activation_id = ? AND status = 'RUNNING'
                            """)) {
                        update.setTimestamp(1, Timestamp.from(availableAt));
                        update.setString(2, row.activationId());
                        requireOne(update.executeUpdate(), "expired activation changed concurrently");
                    }
                    insertOutbox(connection, row.activationId(), row.workerGroup(), row.slotNo(), availableAt);
                } else {
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE tx_workflow_node_activation
                               SET status = 'FAILED', lease_until = NULL, finished_at = ?
                             WHERE activation_id = ? AND status = 'RUNNING'
                            """)) {
                        update.setTimestamp(1, Timestamp.from(now));
                        update.setString(2, row.activationId());
                        requireOne(update.executeUpdate(), "expired activation changed concurrently");
                    }
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE tx_workflow_instance
                               SET status = 'FAILED', finished_at = ?
                             WHERE instance_id = ? AND status = 'RUNNING'
                            """)) {
                        update.setTimestamp(1, Timestamp.from(now));
                        update.setString(2, row.instanceId());
                        requireOne(update.executeUpdate(), "workflow instance is no longer RUNNING");
                    }
                    finishRootExecution(connection, row.instanceId(), "FAILED");
                    failedRootExecutions.add(row.triggerExecutionId());
                }
            }
            return List.copyOf(failedRootExecutions);
        });
    }

    @Override
    public List<OutboxEvent> findUnpublishedOutbox(int limit) {
        String sql = """
                SELECT outbox_id, activation_id, worker_group, slot_no, available_at
                  FROM tx_workflow_outbox
                 WHERE published_at IS NULL
                 ORDER BY outbox_id
                 LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                List<OutboxEvent> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new OutboxEvent(
                            rs.getLong("outbox_id"),
                            rs.getString("activation_id"),
                            rs.getString("worker_group"),
                            rs.getInt("slot_no"),
                            rs.getTimestamp("available_at").toInstant()
                    ));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new MetaException("find workflow outbox", ex);
        }
    }

    @Override
    public void markOutboxPublished(long outboxId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE tx_workflow_outbox
                        SET published_at = CURRENT_TIMESTAMP(3)
                      WHERE outbox_id = ? AND published_at IS NULL
                     """)) {
            statement.setLong(1, outboxId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new MetaException("mark workflow outbox " + outboxId, ex);
        }
    }

    private static String lockRunningActivation(
            Connection connection,
            String activationId,
            int attempt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT instance_id
                  FROM tx_workflow_node_activation
                 WHERE activation_id = ? AND status = 'RUNNING' AND current_attempt = ?
                   FOR UPDATE
                """)) {
            statement.setString(1, activationId);
            statement.setInt(2, attempt);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("activation is no longer RUNNING: " + activationId);
                }
                return rs.getString(1);
            }
        }
    }

    private static void finishAttempt(
            Connection connection,
            String activationId,
            int attempt,
            String status,
            String errorCode,
            String errorMessage,
            java.time.Instant finishedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE tx_workflow_node_attempt
                   SET status = ?, error_code = ?, error_message = ?, finished_at = ?
                 WHERE activation_id = ? AND attempt_no = ? AND status = 'RUNNING'
                """)) {
            statement.setString(1, status);
            statement.setString(2, errorCode);
            statement.setString(3, errorMessage);
            statement.setTimestamp(4, Timestamp.from(finishedAt));
            statement.setString(5, activationId);
            statement.setInt(6, attempt);
            requireOne(statement.executeUpdate(), "workflow attempt is no longer RUNNING");
        }
    }

    private static boolean insertToken(
            Connection connection,
            String instanceId,
            String sourceActivationId,
            String edgeId,
            String targetNodeId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO tx_workflow_token
                    (instance_id, source_activation_id, edge_id, target_node_id)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setString(1, instanceId);
            statement.setString(2, sourceActivationId);
            statement.setString(3, edgeId);
            statement.setString(4, targetNodeId);
            return statement.executeUpdate() == 1;
        }
    }

    private static void insertActivationAndOutbox(
            Connection connection,
            String instanceId,
            ActivationSpec activation
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO tx_workflow_node_activation
                    (activation_id, instance_id, node_id, activation_no, status,
                     worker_group, slot_no, max_attempts, retry_interval_sec,
                     timeout_seconds, input_data, available_at)
                VALUES (?, ?, ?, ?, 'READY', ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, activation.activationId());
            statement.setString(2, instanceId);
            statement.setString(3, activation.nodeId());
            statement.setInt(4, activation.activationNo());
            statement.setString(5, activation.workerGroup());
            statement.setInt(6, activation.slotNo());
            statement.setInt(7, activation.maxAttempts());
            statement.setInt(8, activation.retryIntervalSeconds());
            statement.setInt(9, activation.timeoutSeconds());
            statement.setString(10, activation.inputJson());
            statement.setTimestamp(11, Timestamp.from(activation.availableAt()));
            statement.executeUpdate();
        }
        insertOutbox(
                connection,
                activation.activationId(),
                activation.workerGroup(),
                activation.slotNo(),
                activation.availableAt()
        );
    }

    private static void insertOutbox(
            Connection connection,
            String activationId,
            String workerGroup,
            int slotNo,
            java.time.Instant availableAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO tx_workflow_outbox
                    (activation_id, worker_group, slot_no, available_at)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setString(1, activationId);
            statement.setString(2, workerGroup);
            statement.setInt(3, slotNo);
            statement.setTimestamp(4, Timestamp.from(availableAt));
            statement.executeUpdate();
        }
    }

    private static void finishRootExecution(
            Connection connection,
            String instanceId,
            String status
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE tx_execution e
                JOIN tx_workflow_instance i ON i.trigger_execution_id = e.id
                   SET e.status = ?,
                       e.finished_at = CURRENT_TIMESTAMP(3)
                 WHERE i.instance_id = ? AND e.status = 'RUNNING'
                """)) {
            statement.setString(1, status);
            statement.setString(2, instanceId);
            requireOne(statement.executeUpdate(), "root execution is no longer RUNNING");
        }
    }

    private static void requireOne(int count, String message) {
        if (count != 1) {
            throw new IllegalStateException(message);
        }
    }

    private <T> T tx(String action, SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T value = work.run(connection);
                connection.commit();
                return value;
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                throw ex instanceof RuntimeException runtime ? runtime : new MetaException(action, ex);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new MetaException(action, ex);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private record ExpiredActivation(
            String activationId,
            String instanceId,
            int attempt,
            int maxAttempts,
            int retryIntervalSeconds,
            String workerGroup,
            int slotNo,
            long triggerExecutionId
    ) {
    }
}
