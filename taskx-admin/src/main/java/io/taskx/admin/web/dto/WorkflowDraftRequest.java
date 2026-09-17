package io.taskx.admin.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkflowDraftRequest(
        String name,
        long expectedRevision,
        JsonNode definition,
        JsonNode uiLayout
) {
}
