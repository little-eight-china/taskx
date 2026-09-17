package io.taskx.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface WorkflowHandler {

    JsonNode handle(NodeContext context) throws Exception;
}
