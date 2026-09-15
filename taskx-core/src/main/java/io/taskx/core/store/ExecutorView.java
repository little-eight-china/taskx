package io.taskx.core.store;

import java.time.Instant;

public record ExecutorView(String executorId, Instant lastHeartbeatAt, Instant createdAt) {
}
