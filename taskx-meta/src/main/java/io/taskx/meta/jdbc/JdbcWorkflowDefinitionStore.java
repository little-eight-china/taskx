package io.taskx.meta.jdbc;

import io.taskx.meta.MetaException;
import io.taskx.workflow.definition.WorkflowDraft;
import io.taskx.workflow.definition.WorkflowVersion;
import io.taskx.workflow.store.WorkflowDefinitionStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class JdbcWorkflowDefinitionStore implements WorkflowDefinitionStore {

    private final DataSource dataSource;

    public JdbcWorkflowDefinitionStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<WorkflowDraft> findDraft(String workflowId) {
        String sql = """
                SELECT w.workflow_id, w.name, d.revision, d.definition_json, d.ui_layout_json
                  FROM tx_workflow w
                  JOIN tx_workflow_draft d ON d.workflow_id = w.workflow_id
                 WHERE w.workflow_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, workflowId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapDraft(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new MetaException("find workflow draft " + workflowId, ex);
        }
    }

    @Override
    public WorkflowDraft saveDraft(
            String workflowId,
            String name,
            long expectedRevision,
            String definitionJson,
            String uiLayoutJson
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Long revision = lockDraftRevision(connection, workflowId);
                long nextRevision;
                if (revision == null) {
                    if (expectedRevision != 0) {
                        throw new IllegalStateException("workflow draft does not exist; expected revision must be 0");
                    }
                    insertWorkflow(connection, workflowId, name);
                    nextRevision = 1;
                    insertDraft(connection, workflowId, nextRevision, definitionJson, uiLayoutJson);
                } else {
                    if (revision != expectedRevision) {
                        throw new IllegalStateException(
                                "workflow draft changed: expected revision " + expectedRevision + ", was " + revision);
                    }
                    nextRevision = revision + 1;
                    updateWorkflowName(connection, workflowId, name);
                    updateDraft(connection, workflowId, nextRevision, definitionJson, uiLayoutJson);
                }
                connection.commit();
                return new WorkflowDraft(workflowId, name, nextRevision, definitionJson, uiLayoutJson);
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                throw wrap("save workflow draft " + workflowId, ex);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new MetaException("save workflow draft " + workflowId, ex);
        }
    }

    @Override
    public WorkflowVersion publish(
            String workflowId,
            long expectedDraftRevision,
            String definitionHash,
            Instant publishedAt
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LockedWorkflow locked = lockWorkflow(connection, workflowId);
                if (locked == null) {
                    throw new IllegalStateException("workflow draft not found: " + workflowId);
                }
                if (locked.revision() != expectedDraftRevision) {
                    throw new IllegalStateException(
                            "workflow draft changed: expected revision " + expectedDraftRevision
                                    + ", was " + locked.revision());
                }
                int version = locked.currentVersion() == null ? 1 : locked.currentVersion() + 1;
                insertVersion(connection, locked, version, definitionHash, publishedAt);
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE tx_workflow SET current_published_version = ? WHERE workflow_id = ?")) {
                    update.setInt(1, version);
                    update.setString(2, workflowId);
                    update.executeUpdate();
                }
                connection.commit();
                return new WorkflowVersion(
                        workflowId,
                        version,
                        locked.name(),
                        locked.definitionJson(),
                        locked.uiLayoutJson(),
                        definitionHash,
                        WorkflowVersion.Status.PUBLISHED,
                        publishedAt
                );
            } catch (RuntimeException | SQLException ex) {
                connection.rollback();
                throw wrap("publish workflow " + workflowId, ex);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new MetaException("publish workflow " + workflowId, ex);
        }
    }

    @Override
    public Optional<WorkflowVersion> findVersion(String workflowId, int version) {
        String sql = """
                SELECT v.workflow_id, v.version, w.name, v.definition_json, v.ui_layout_json,
                       v.definition_hash, v.status, v.published_at
                  FROM tx_workflow_version v
                  JOIN tx_workflow w ON w.workflow_id = v.workflow_id
                 WHERE v.workflow_id = ? AND v.version = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, workflowId);
            statement.setInt(2, version);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapVersion(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new MetaException("find workflow version " + workflowId + '/' + version, ex);
        }
    }

    @Override
    public Optional<WorkflowVersion> findCurrentPublished(String workflowId) {
        String sql = """
                SELECT v.workflow_id, v.version, w.name, v.definition_json, v.ui_layout_json,
                       v.definition_hash, v.status, v.published_at
                  FROM tx_workflow w
                  JOIN tx_workflow_version v
                    ON v.workflow_id = w.workflow_id AND v.version = w.current_published_version
                 WHERE w.workflow_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, workflowId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapVersion(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new MetaException("find current workflow " + workflowId, ex);
        }
    }

    private static Long lockDraftRevision(Connection connection, String workflowId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT revision FROM tx_workflow_draft WHERE workflow_id = ? FOR UPDATE")) {
            statement.setString(1, workflowId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static LockedWorkflow lockWorkflow(Connection connection, String workflowId) throws SQLException {
        String sql = """
                SELECT w.name, w.current_published_version, d.revision, d.definition_json, d.ui_layout_json
                  FROM tx_workflow w
                  JOIN tx_workflow_draft d ON d.workflow_id = w.workflow_id
                 WHERE w.workflow_id = ?
                   FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, workflowId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                int current = rs.getInt("current_published_version");
                Integer currentVersion = rs.wasNull() ? null : current;
                return new LockedWorkflow(
                        workflowId,
                        rs.getString("name"),
                        currentVersion,
                        rs.getLong("revision"),
                        rs.getString("definition_json"),
                        rs.getString("ui_layout_json")
                );
            }
        }
    }

    private static void insertWorkflow(Connection connection, String workflowId, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tx_workflow (workflow_id, name) VALUES (?, ?)")) {
            statement.setString(1, workflowId);
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    private static void updateWorkflowName(Connection connection, String workflowId, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tx_workflow SET name = ? WHERE workflow_id = ?")) {
            statement.setString(1, name);
            statement.setString(2, workflowId);
            statement.executeUpdate();
        }
    }

    private static void insertDraft(
            Connection connection,
            String workflowId,
            long revision,
            String definitionJson,
            String uiLayoutJson
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO tx_workflow_draft
                    (workflow_id, revision, definition_json, ui_layout_json)
                VALUES (?, ?, ?, ?)
                """)) {
            setDraft(statement, workflowId, revision, definitionJson, uiLayoutJson);
            statement.executeUpdate();
        }
    }

    private static void updateDraft(
            Connection connection,
            String workflowId,
            long revision,
            String definitionJson,
            String uiLayoutJson
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE tx_workflow_draft
                   SET revision = ?, definition_json = ?, ui_layout_json = ?
                 WHERE workflow_id = ?
                """)) {
            statement.setLong(1, revision);
            statement.setString(2, definitionJson);
            statement.setString(3, uiLayoutJson);
            statement.setString(4, workflowId);
            statement.executeUpdate();
        }
    }

    private static void setDraft(
            PreparedStatement statement,
            String workflowId,
            long revision,
            String definitionJson,
            String uiLayoutJson
    ) throws SQLException {
        statement.setString(1, workflowId);
        statement.setLong(2, revision);
        statement.setString(3, definitionJson);
        statement.setString(4, uiLayoutJson);
    }

    private static void insertVersion(
            Connection connection,
            LockedWorkflow locked,
            int version,
            String definitionHash,
            Instant publishedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO tx_workflow_version
                    (workflow_id, version, definition_json, ui_layout_json,
                     definition_hash, status, published_at)
                SELECT workflow_id, ?, definition_json, ui_layout_json, ?, 'PUBLISHED', ?
                  FROM tx_workflow_draft
                 WHERE workflow_id = ?
                """)) {
            statement.setInt(1, version);
            statement.setString(2, definitionHash);
            statement.setTimestamp(3, Timestamp.from(publishedAt));
            statement.setString(4, locked.workflowId());
            statement.executeUpdate();
        }
    }

    private static WorkflowDraft mapDraft(ResultSet rs) throws SQLException {
        return new WorkflowDraft(
                rs.getString("workflow_id"),
                rs.getString("name"),
                rs.getLong("revision"),
                rs.getString("definition_json"),
                rs.getString("ui_layout_json")
        );
    }

    private static WorkflowVersion mapVersion(ResultSet rs) throws SQLException {
        return new WorkflowVersion(
                rs.getString("workflow_id"),
                rs.getInt("version"),
                rs.getString("name"),
                rs.getString("definition_json"),
                rs.getString("ui_layout_json"),
                rs.getString("definition_hash"),
                WorkflowVersion.Status.valueOf(rs.getString("status")),
                rs.getTimestamp("published_at").toInstant()
        );
    }

    private static RuntimeException wrap(String action, Exception ex) {
        return ex instanceof RuntimeException runtime ? runtime : new MetaException(action, ex);
    }

    private record LockedWorkflow(
            String workflowId,
            String name,
            Integer currentVersion,
            long revision,
            String definitionJson,
            String uiLayoutJson
    ) {
    }
}
