package io.taskx.workflow.store;

import io.taskx.workflow.definition.WorkflowDraft;
import io.taskx.workflow.definition.WorkflowVersion;

import java.time.Instant;
import java.util.Optional;

public interface WorkflowDefinitionStore {

    Optional<WorkflowDraft> findDraft(String workflowId);

    /**
     * Optimistic draft write. For a new workflow expectedRevision is 0; otherwise it must equal the current revision.
     */
    WorkflowDraft saveDraft(
            String workflowId,
            String name,
            long expectedRevision,
            String definitionJson,
            String uiLayoutJson
    );

    /**
     * Atomically snapshots the current draft as the next immutable version and updates current_published_version.
     */
    WorkflowVersion publish(
            String workflowId,
            long expectedDraftRevision,
            String definitionHash,
            Instant publishedAt
    );

    Optional<WorkflowVersion> findVersion(String workflowId, int version);

    Optional<WorkflowVersion> findCurrentPublished(String workflowId);
}
