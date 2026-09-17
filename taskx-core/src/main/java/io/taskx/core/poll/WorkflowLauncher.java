package io.taskx.core.poll;

import io.taskx.core.domain.Execution;
import io.taskx.core.domain.Task;
import io.taskx.core.domain.TaskTarget;

/**
 * Starts an asynchronous workflow for a root task execution.
 * The workflow engine, not {@link Dispatch}, owns the root terminal transition.
 */
@FunctionalInterface
public interface WorkflowLauncher {

    void launch(Task task, TaskTarget.Workflow target, Execution rootExecution) throws Exception;
}
