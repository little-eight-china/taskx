package io.taskx.workflow.node;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WorkflowHandlerRegistry {

    private final Map<String, WorkflowHandler> handlers;

    public WorkflowHandlerRegistry(Map<String, ? extends WorkflowHandler> handlers) {
        this.handlers = Map.copyOf(Objects.requireNonNull(handlers, "handlers"));
    }

    public Optional<WorkflowHandler> find(String name) {
        return Optional.ofNullable(handlers.get(name));
    }
}
