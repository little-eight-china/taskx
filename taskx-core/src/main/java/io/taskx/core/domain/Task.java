package io.taskx.core.domain;

import io.taskx.core.Require;
import io.taskx.core.slot.SlotConfig;

import java.util.Objects;

/**
 * Scheduling definition. Slot is {@code hash(id) % N}, not stored on the row.
 *
 * @param payload target arguments as JSON text; may be {@code null}
 */
public record Task(
        String id,
        String payload,
        boolean enabled,
        Trigger trigger,
        TaskTarget target
) {

    public Task {
        id = Require.notBlank(id, "task id");
        trigger = Objects.requireNonNull(trigger, "trigger");
        target = Objects.requireNonNull(target, "target");
    }

    public Task(
            String id,
            String handler,
            String payload,
            boolean enabled,
            Trigger trigger,
            String legacyWorkflowJson
    ) {
        this(id, payload, enabled, trigger, legacyTarget(handler, legacyWorkflowJson));
    }

    public int slot(SlotConfig slots) {
        return Objects.requireNonNull(slots, "slots").slotOf(id);
    }

    public Task withEnabled(boolean enabled) {
        return new Task(id, payload, enabled, trigger, target);
    }

    private static TaskTarget legacyTarget(String handler, String legacyWorkflowJson) {
        if (legacyWorkflowJson != null && !legacyWorkflowJson.isBlank()) {
            throw new IllegalArgumentException(
                    "inline workflowJson is no longer supported; publish a workflow and use TaskTarget.Workflow");
        }
        return new TaskTarget.Handler(handler);
    }
}
