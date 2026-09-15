package io.taskx.admin.web.dto;

import io.taskx.core.domain.Task;

public record TaskWriteRequest(
        String handler,
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
        return new Task(id, handler, payload, on, trigger.toTrigger(), workflowJson);
    }
}
