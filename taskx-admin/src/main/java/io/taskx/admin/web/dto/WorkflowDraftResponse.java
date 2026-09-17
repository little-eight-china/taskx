package io.taskx.admin.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.taskx.workflow.definition.WorkflowDraft;

public record WorkflowDraftResponse(
        String workflowId,
        String name,
        long revision,
        JsonNode definition,
        JsonNode uiLayout
) {

    public static WorkflowDraftResponse from(WorkflowDraft draft, JsonNode definition, JsonNode uiLayout) {
        return new WorkflowDraftResponse(
                draft.workflowId(),
                draft.name(),
                draft.revision(),
                definition,
                uiLayout
        );
    }
}
