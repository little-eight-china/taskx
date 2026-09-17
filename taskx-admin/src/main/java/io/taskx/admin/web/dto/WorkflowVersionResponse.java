package io.taskx.admin.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.taskx.workflow.definition.WorkflowVersion;

import java.time.Instant;

public record WorkflowVersionResponse(
        String workflowId,
        String name,
        int version,
        String status,
        String definitionHash,
        Instant publishedAt,
        JsonNode definition,
        JsonNode uiLayout
) {

    public static WorkflowVersionResponse from(
            WorkflowVersion version,
            JsonNode definition,
            JsonNode uiLayout
    ) {
        return new WorkflowVersionResponse(
                version.workflowId(),
                version.name(),
                version.version(),
                version.status().name(),
                version.definitionHash(),
                version.publishedAt(),
                definition,
                uiLayout
        );
    }
}
