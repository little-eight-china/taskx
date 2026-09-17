package io.taskx.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * Config: {@code {"handler":"bean-or-registry-name"}}.
 */
public final class HandlerNodeExecutor implements WorkflowNodeExecutor {

    private final WorkflowHandlerRegistry handlers;

    public HandlerNodeExecutor(WorkflowHandlerRegistry handlers) {
        this.handlers = Objects.requireNonNull(handlers, "handlers");
    }

    @Override
    public String type() {
        return "HANDLER";
    }

    @Override
    public int configVersion() {
        return 1;
    }

    @Override
    public void validate(JsonNode config) {
        handlerName(config);
    }

    @Override
    public NodeResult execute(NodeContext context, JsonNode config) throws Exception {
        String name = handlerName(config);
        WorkflowHandler handler = handlers.find(name)
                .orElseThrow(() -> new IllegalStateException("workflow handler not registered: " + name));
        return NodeResult.success(handler.handle(context));
    }

    private static String handlerName(JsonNode config) {
        if (config == null || !config.isObject()) {
            throw new IllegalArgumentException("HANDLER config must be an object");
        }
        JsonNode value = config.get("handler");
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("HANDLER config.handler must not be blank");
        }
        return value.asText();
    }
}
