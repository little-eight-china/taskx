package io.taskx.workflow.runtime;

import io.taskx.core.domain.Execution;
import io.taskx.core.domain.Task;
import io.taskx.core.domain.TaskTarget;
import io.taskx.core.poll.WorkflowLauncher;

import java.util.Objects;

public final class EngineWorkflowLauncher implements WorkflowLauncher {

    private final WorkflowEngine engine;

    public EngineWorkflowLauncher(WorkflowEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public void launch(Task task, TaskTarget.Workflow target, Execution rootExecution) {
        engine.start(
                target.workflowId(),
                target.pinnedVersion(),
                rootExecution.id(),
                task.payload()
        );
    }
}
