package io.taskx.core.store;

import io.taskx.core.domain.Execution;
import io.taskx.core.domain.ExecutionStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryExecutionRepositoryTest {

    private final InMemoryExecutionRepository store = new InMemoryExecutionRepository();

    @Test
    void uniqueKeyRejectsDuplicateFire() {
        Execution first = store.insertPending(Execution.pending("task-a", 1_000L, "ex-1")).orElseThrow();
        Optional<Execution> duplicate = store.insertPending(Execution.pending("task-a", 1_000L, "ex-2"));
        assertTrue(duplicate.isEmpty());
        Execution overlap = store.insertPending(Execution.pending("task-a", 1_001L, "ex-1")).orElseThrow();
        assertNotEquals(first.id(), overlap.id());
    }

    @Test
    void casGrantsExecutionRightOnce() {
        Execution row = store.insertPending(Execution.pending("task-a", 1_000L, "ex-1")).orElseThrow();
        assertTrue(store.casStatus(row.id(), ExecutionStatus.PENDING, ExecutionStatus.RUNNING));
        assertFalse(store.casStatus(row.id(), ExecutionStatus.PENDING, ExecutionStatus.RUNNING));
        assertTrue(store.casStatus(row.id(), ExecutionStatus.RUNNING, ExecutionStatus.SUCCESS));
    }

    @Test
    void recoverySeesOnlyOwnPending() {
        store.insertPending(Execution.pending("task-a", 1_000L, "ex-1"));
        Execution running = store.insertPending(Execution.pending("task-b", 1_000L, "ex-1")).orElseThrow();
        store.casStatus(running.id(), ExecutionStatus.PENDING, ExecutionStatus.RUNNING);
        store.insertPending(Execution.pending("task-c", 1_000L, "ex-2"));

        assertEquals(1, store.findPendingByExecutorId("ex-1").size());
        assertEquals("task-a", store.findPendingByExecutorId("ex-1").getFirst().taskId());
        assertEquals(1, store.findPendingByExecutorId("ex-2").size());
    }
}
