package io.taskx.core.handler;

import io.taskx.core.domain.Execution;
import io.taskx.core.domain.Task;

@FunctionalInterface
public interface TaskHandler {

    void handle(Task task, Execution execution) throws Exception;
}
