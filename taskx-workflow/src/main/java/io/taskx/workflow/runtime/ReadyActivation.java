package io.taskx.workflow.runtime;

import java.time.Instant;

public record ReadyActivation(
        String activationId,
        String workerGroup,
        int slotNo,
        Instant availableAt
) {
}
