package io.taskx.core.domain;

import io.taskx.core.slot.SlotConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainModelTest {

    @Test
    void taskComputesSlotFromId() {
        Task task = new Task("hello", "demoHandler", "{}", true, new Trigger.FixedRate(60), null);
        assertEquals(6, task.slot(SlotConfig.DEFAULT));
    }

    @Test
    void taskAndTriggerRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new Task(" ", "h", null, true, new Trigger.Once(1L), null));
        assertThrows(IllegalArgumentException.class, () -> new Trigger.FixedRate(0));
        assertThrows(IllegalArgumentException.class, () -> new Trigger.Cron(" "));
        assertThrows(IllegalArgumentException.class, () -> new SlotOwnership(-1, "ex-1"));
    }

    @Test
    void executionStatusTransitions() {
        assertTrue(ExecutionStatus.PENDING.canTransitionTo(ExecutionStatus.RUNNING));
        assertTrue(ExecutionStatus.PENDING.canTransitionTo(ExecutionStatus.FAILED));
        assertTrue(ExecutionStatus.PENDING.canTransitionTo(ExecutionStatus.CANCELLED));
        assertTrue(ExecutionStatus.RUNNING.canTransitionTo(ExecutionStatus.SUCCESS));
        assertTrue(ExecutionStatus.RUNNING.canTransitionTo(ExecutionStatus.FAILED));
        assertFalse(ExecutionStatus.RUNNING.canTransitionTo(ExecutionStatus.PENDING));
        assertFalse(ExecutionStatus.SUCCESS.canTransitionTo(ExecutionStatus.RUNNING));
    }
}
