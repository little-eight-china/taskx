package io.taskx.workflow.runtime;

import java.time.Instant;
import java.util.Objects;

public record OutboxEvent(
        long id,
        String activationId,
        String workerGroup,
        int slotNo,
        Instant availableAt
) {

    public OutboxEvent {
        activationId = Objects.requireNonNull(activationId, "activationId");
        workerGroup = Objects.requireNonNull(workerGroup, "workerGroup");
        availableAt = Objects.requireNonNull(availableAt, "availableAt");
    }
}
