package io.taskx.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A plugin for one workflow node type. Implementations must tolerate at-least-once invocation.
 */
public interface WorkflowNodeExecutor {

    String type();

    int configVersion();

    void validate(JsonNode config);

    NodeResult execute(NodeContext context, JsonNode config) throws Exception;
}
