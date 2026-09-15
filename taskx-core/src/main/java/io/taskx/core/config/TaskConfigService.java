package io.taskx.core.config;

import io.taskx.core.domain.Task;

/**
 * Create/update/disable/delete a task: slot lock, uncommitted MySQL, then Redis, then COMMIT.
 */
public interface TaskConfigService {

    void save(Task task);

    void disable(String taskId);

    void delete(String taskId);
}
