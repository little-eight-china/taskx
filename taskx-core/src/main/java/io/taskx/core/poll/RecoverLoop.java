package io.taskx.core.poll;

import io.taskx.core.store.ExecutionRepository;

import java.util.Objects;

public final class RecoverLoop {

    private final String executorId;
    private final ExecutionRepository executions;
    private final Dispatch dispatch;

    public RecoverLoop(String executorId, ExecutionRepository executions, Dispatch dispatch) {
        this.executorId = Objects.requireNonNull(executorId);
        this.executions = Objects.requireNonNull(executions);
        this.dispatch = Objects.requireNonNull(dispatch);
    }

    public void tick() {
        executions.findPendingByExecutorId(executorId).forEach(dispatch::recover);
    }
}
