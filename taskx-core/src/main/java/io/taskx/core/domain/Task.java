package io.taskx.core.domain;

import io.taskx.core.Require;
import io.taskx.core.slot.SlotConfig;

import java.util.Objects;

/**
 * Scheduling definition. Slot is {@code hash(id) % N}, not stored on the row.
 *
 * @param payload       handler arguments as JSON text; may be {@code null}
 * @param workflowJson  optional workflow graph JSON; may be {@code null}
 */
public record Task(
        String id,
        String handler,
        String payload,
        boolean enabled,
        Trigger trigger,
        String workflowJson
) {

    public Task {
        id = Require.notBlank(id, "task id");
        handler = Require.notBlank(handler, "handler");
        trigger = Objects.requireNonNull(trigger, "trigger");
    }

    public int slot(SlotConfig slots) {
        return Objects.requireNonNull(slots, "slots").slotOf(id);
    }

    public Task withEnabled(boolean enabled) {
        return new Task(id, handler, payload, enabled, trigger, workflowJson);
    }
}
