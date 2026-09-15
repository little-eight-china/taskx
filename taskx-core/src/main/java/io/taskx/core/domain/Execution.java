package io.taskx.core.domain;

import io.taskx.core.Require;

import java.util.Objects;

/**
 * One fire of a task. Unique on {@code (taskId, scheduledFireTime)}.
 *
 * @param id database id; {@code null} before insert
 */
public record Execution(
        Long id,
        String taskId,
        long scheduledFireTime,
        String executorId,
        ExecutionStatus status
) {

    public Execution {
        taskId = Require.notBlank(taskId, "taskId");
        executorId = Require.notBlank(executorId, "executorId");
        status = Objects.requireNonNull(status, "status");
    }

    public static Execution pending(String taskId, long scheduledFireTime, String executorId) {
        return new Execution(null, taskId, scheduledFireTime, executorId, ExecutionStatus.PENDING);
    }

    public Execution withId(long id) {
        return new Execution(id, taskId, scheduledFireTime, executorId, status);
    }

    public Execution withStatus(ExecutionStatus status) {
        return new Execution(id, taskId, scheduledFireTime, executorId, status);
    }
}
