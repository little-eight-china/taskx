package io.taskx.workflow.definition;

import java.time.Instant;
import java.util.Objects;

public record WorkflowVersion(
        String workflowId,
        int version,
        String name,
        String definitionJson,
        String uiLayoutJson,
        String definitionHash,
        Status status,
        Instant publishedAt
) {

    public enum Status {
        PUBLISHED,
        ARCHIVED
    }

    public WorkflowVersion {
        workflowId = requireText(workflowId, "workflowId");
        name = requireText(name, "name");
        if (version <= 0) {
            throw new IllegalArgumentException("version must be > 0");
        }
        definitionJson = requireText(definitionJson, "definitionJson");
        definitionHash = requireText(definitionHash, "definitionHash");
        status = Objects.requireNonNull(status, "status");
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
