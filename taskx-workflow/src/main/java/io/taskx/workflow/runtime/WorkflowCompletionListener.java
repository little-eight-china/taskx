package io.taskx.workflow.runtime;

@FunctionalInterface
public interface WorkflowCompletionListener {

    WorkflowCompletionListener NOOP = (rootExecutionId, status) -> {
    };

    void onTerminal(long rootExecutionId, WorkflowInstanceStatus status);
}
