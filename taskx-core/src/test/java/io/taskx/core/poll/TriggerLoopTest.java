package io.taskx.core.poll;

import io.taskx.common.lock.InMemoryLock;
import io.taskx.core.clock.EpochClock;
import io.taskx.core.domain.Execution;
import io.taskx.core.domain.ExecutionStatus;
import io.taskx.core.domain.Task;
import io.taskx.core.domain.Trigger;
import io.taskx.core.fake.InMemorySlotOwnershipRepository;
import io.taskx.core.fake.InMemoryTaskRepository;
import io.taskx.core.fake.InMemoryTriggerIndex;
import io.taskx.core.handler.MapTaskHandlerRegistry;
import io.taskx.core.schedule.NextFireCalculator;
import io.taskx.core.slot.SlotConfig;
import io.taskx.core.store.InMemoryExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerLoopTest {

    private static final String EXECUTOR = "ex-1";
    private static final int SLOT = 0;
    private static final long NOW = 1_000L;

    private final InMemoryLock lock = new InMemoryLock();
    private final InMemorySlotOwnershipRepository ownership = new InMemorySlotOwnershipRepository();
    private final InMemoryTriggerIndex index = new InMemoryTriggerIndex();
    private final InMemoryTaskRepository tasks = new InMemoryTaskRepository();
    private final InMemoryExecutionRepository executions = new InMemoryExecutionRepository();
    private final NextFireCalculator calculator = new NextFireCalculator();
    private final SlotConfig slots = new SlotConfig(1);
    private final AtomicInteger handled = new AtomicInteger();
    private MapTaskHandlerRegistry handlers;
    private Dispatch dispatch;
    private TriggerLoop loop;

    @BeforeEach
    void setUp() {
        ownership.claim(SLOT, EXECUTOR);
        handlers = new MapTaskHandlerRegistry().put("demo", (task, execution) -> handled.incrementAndGet());
        dispatch = new Dispatch(
                EXECUTOR,
                slots,
                EpochClock.fixed(NOW),
                calculator,
                tasks,
                executions,
                handlers,
                Runnable::run,
                new LockingTerminalScoreWriter(lock, index)
        );
        loop = new TriggerLoop(
                EXECUTOR,
                lock,
                ownership,
                index,
                tasks,
                EpochClock.fixed(NOW),
                calculator,
                dispatch,
                10
        );
    }

    @Test
    void claimsDueTaskWritesNextAndRunsHandler() {
        Task task = rateTask("hello", true);
        tasks.save(task);
        index.add(SLOT, task.id(), NOW);

        loop.tick(SLOT);

        assertEquals(NOW + 60, index.score(SLOT, task.id()));
        assertEquals(1, handled.get());
        Execution row = executions.all().getFirst();
        assertEquals(ExecutionStatus.SUCCESS, row.status());
        assertEquals(NOW, row.scheduledFireTime());
    }

    @Test
    void disabledTaskIsRemovedWithoutExecution() {
        Task task = rateTask("hello", false);
        tasks.save(task);
        index.add(SLOT, task.id(), NOW);

        loop.tick(SLOT);

        assertNull(index.score(SLOT, task.id()));
        assertTrue(executions.all().isEmpty());
        assertEquals(0, handled.get());
    }

    @Test
    void skipsWhenLockHeld() {
        Task task = rateTask("hello", true);
        tasks.save(task);
        index.add(SLOT, task.id(), NOW);
        lock.tryLock("lock:slot:0").orElseThrow();

        loop.tick(SLOT);

        assertEquals(NOW, index.score(SLOT, task.id()));
        assertTrue(executions.all().isEmpty());
    }

    @Test
    void skipsWhenOwnerMismatch() {
        ownership.claim(1, EXECUTOR);
        InMemorySlotOwnershipRepository other = new InMemorySlotOwnershipRepository();
        other.claim(SLOT, "ex-other");
        loop = new TriggerLoop(
                EXECUTOR,
                lock,
                other,
                index,
                tasks,
                EpochClock.fixed(NOW),
                calculator,
                dispatch,
                10
        );
        Task task = rateTask("hello", true);
        tasks.save(task);
        index.add(SLOT, task.id(), NOW);

        loop.tick(SLOT);

        assertTrue(executions.all().isEmpty());
        assertEquals(NOW, index.score(SLOT, task.id()));
    }

    @Test
    void submitRejectMarksFailed() {
        dispatch = new Dispatch(
                EXECUTOR,
                slots,
                EpochClock.fixed(NOW),
                calculator,
                tasks,
                executions,
                handlers,
                command -> {
                    throw new RejectedExecutionException("full");
                },
                new LockingTerminalScoreWriter(lock, index)
        );
        loop = new TriggerLoop(
                EXECUTOR,
                lock,
                ownership,
                index,
                tasks,
                EpochClock.fixed(NOW),
                calculator,
                dispatch,
                10
        );
        Task task = rateTask("hello", true);
        tasks.save(task);
        index.add(SLOT, task.id(), NOW);

        loop.tick(SLOT);

        assertEquals(ExecutionStatus.FAILED, executions.all().getFirst().status());
        assertEquals(0, handled.get());
        assertEquals(NOW + 60, index.score(SLOT, task.id()));
    }

    @Test
    void fixedDelayRemovesOnClaimAndWritesOnTerminal() {
        Task task = new Task("hello", "demo", null, true, new Trigger.FixedDelay(30), null);
        tasks.save(task);
        index.add(SLOT, task.id(), NOW);

        loop.tick(SLOT);

        assertEquals(1, handled.get());
        assertEquals(NOW + 30, index.score(SLOT, task.id()));
    }

    @Test
    void recoverRunsOwnPending() {
        Task task = rateTask("hello", true);
        tasks.save(task);
        Execution pending = executions.insertPending(Execution.pending(task.id(), NOW, EXECUTOR)).orElseThrow();

        new RecoverLoop(EXECUTOR, executions, dispatch).tick();

        assertEquals(ExecutionStatus.SUCCESS, executions.findById(pending.id()).orElseThrow().status());
        assertEquals(1, handled.get());
    }

    private static Task rateTask(String id, boolean enabled) {
        return new Task(id, "demo", null, enabled, new Trigger.FixedRate(60), null);
    }
}
