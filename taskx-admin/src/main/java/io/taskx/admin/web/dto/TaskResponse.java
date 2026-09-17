package io.taskx.admin.web.dto;

import io.taskx.core.domain.Task;
import io.taskx.core.domain.TaskTarget;
import io.taskx.core.slot.SlotConfig;

public record TaskResponse(
        String id,
        String handler,
        TaskTarget.Type targetType,
        String targetRef,
        Integer targetVersion,
        String payload,
        boolean enabled,
        int slot,
        TriggerBody trigger,
        String workflowJson
) {

    public static TaskResponse from(Task task, SlotConfig slots) {
        return new TaskResponse(
                task.id(),
                task.target() instanceof TaskTarget.Handler handler ? handler.name() : null,
                task.target().type(),
                targetRef(task.target()),
                task.target() instanceof TaskTarget.Workflow workflow ? workflow.pinnedVersion() : null,
                task.payload(),
                task.enabled(),
                task.slot(slots),
                TriggerBody.from(task.trigger()),
                null
        );
    }

    private static String targetRef(TaskTarget target) {
        return switch (target) {
            case TaskTarget.Handler handler -> handler.name();
            case TaskTarget.Workflow workflow -> workflow.workflowId();
        };
    }
}
