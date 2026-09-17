package io.taskx.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record NodeContext(
        String workflowInstanceId,
        String activationId,
        int attempt,
        String idempotencyKey,
        int timeoutSeconds,
        JsonNode workflowInput,
        JsonNode nodeInput
) {

    public NodeContext {
        workflowInstanceId = Objects.requireNonNull(workflowInstanceId, "workflowInstanceId");
        activationId = Objects.requireNonNull(activationId, "activationId");
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be > 0");
        }
        idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (timeoutSeconds < 0) {
            throw new IllegalArgumentException("timeoutSeconds must be >= 0");
        }
    }
}
