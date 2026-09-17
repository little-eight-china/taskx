package io.taskx.workflow.runtime;

import java.time.Instant;
import java.util.Objects;

public record WorkflowInstance(
        String id,
        String workflowId,
        int version,
        long triggerExecutionId,
        WorkflowInstanceStatus status,
        String inputJson,
        String outputJson,
        Instant startedAt,
        Instant finishedAt
) {

    public WorkflowInstance {
        id = Objects.requireNonNull(id, "id");
        workflowId = Objects.requireNonNull(workflowId, "workflowId");
        if (version <= 0) {
            throw new IllegalArgumentException("version must be > 0");
        }
        status = Objects.requireNonNull(status, "status");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
    }
}
