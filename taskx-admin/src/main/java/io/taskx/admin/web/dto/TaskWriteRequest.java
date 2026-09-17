package io.taskx.admin.web.dto;

import io.taskx.core.domain.Task;
import io.taskx.core.domain.TaskTarget;

public record TaskWriteRequest(
        String handler,
        TaskTarget.Type targetType,
        String targetRef,
        Integer targetVersion,
        String payload,
        Boolean enabled,
        TriggerBody trigger,
        String workflowJson
) {

    public Task toTask(String id) {
        if (trigger == null) {
            throw new IllegalArgumentException("trigger is required");
        }
        boolean on = enabled == null || enabled;
        if (workflowJson != null && !workflowJson.isBlank()) {
            throw new IllegalArgumentException(
                    "inline workflowJson is no longer supported; publish a workflow and use targetType=WORKFLOW");
        }
        TaskTarget target;
        if (targetType == null) {
            target = new TaskTarget.Handler(handler);
        } else {
            target = switch (targetType) {
                case HANDLER -> new TaskTarget.Handler(targetRef);
                case WORKFLOW -> new TaskTarget.Workflow(targetRef, targetVersion);
            };
        }
        return new Task(id, payload, on, trigger.toTrigger(), target);
    }
}
