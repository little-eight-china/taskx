package io.taskx.workflow.runtime;

import java.util.Objects;

public record ClaimedActivation(
        String activationId,
        String instanceId,
        String workflowId,
        int workflowVersion,
        long triggerExecutionId,
        String nodeId,
        int activationNo,
        int attempt,
        String inputJson,
        String workflowInputJson
) {

    public ClaimedActivation {
        activationId = Objects.requireNonNull(activationId, "activationId");
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        workflowId = Objects.requireNonNull(workflowId, "workflowId");
        nodeId = Objects.requireNonNull(nodeId, "nodeId");
        if (workflowVersion <= 0 || activationNo <= 0 || attempt <= 0) {
            throw new IllegalArgumentException("version, activationNo and attempt must be > 0");
        }
    }

    public String idempotencyKey() {
        return activationId + ':' + attempt;
    }
}
