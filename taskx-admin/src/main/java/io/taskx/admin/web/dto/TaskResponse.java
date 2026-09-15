package io.taskx.admin.web.dto;

import io.taskx.core.domain.Task;
import io.taskx.core.slot.SlotConfig;

public record TaskResponse(
        String id,
        String handler,
        String payload,
        boolean enabled,
        int slot,
        TriggerBody trigger,
        String workflowJson
) {

    public static TaskResponse from(Task task, SlotConfig slots) {
        return new TaskResponse(
                task.id(),
                task.handler(),
                task.payload(),
                task.enabled(),
                task.slot(slots),
                TriggerBody.from(task.trigger()),
                task.workflowJson()
        );
    }
}
