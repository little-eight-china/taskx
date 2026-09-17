package io.taskx.workflow.definition;

public record WorkflowDraft(
        String workflowId,
        String name,
        long revision,
        String definitionJson,
        String uiLayoutJson
) {

    public WorkflowDraft {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be >= 0");
        }
        if (definitionJson == null || definitionJson.isBlank()) {
            throw new IllegalArgumentException("definitionJson must not be blank");
        }
    }
}
