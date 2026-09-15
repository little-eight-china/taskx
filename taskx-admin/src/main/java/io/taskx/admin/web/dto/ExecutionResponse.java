package io.taskx.admin.web.dto;

import io.taskx.core.domain.Execution;

public record ExecutionResponse(
        long id,
        String taskId,
        long scheduledFireTime,
        String executorId,
        String status
) {

    public static ExecutionResponse from(Execution execution) {
        return new ExecutionResponse(
                execution.id(),
                execution.taskId(),
                execution.scheduledFireTime(),
                execution.executorId(),
                execution.status().name()
        );
    }
}
