package io.taskx.core.runtime;

import io.taskx.core.poll.RecoverLoop;
import io.taskx.core.poll.TriggerLoop;
import io.taskx.core.store.ExecutorRegistry;
import io.taskx.core.store.SlotOwnershipRepository;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Claims slots then polls and recovers on a scheduler.
 */
public final class ExecutorRuntime implements AutoCloseable {

    private final String executorId;
    private final List<Integer> slots;
    private final SlotOwnershipRepository ownership;
    private final ExecutorRegistry executors;
    private final TriggerLoop triggerLoop;
    private final RecoverLoop recoverLoop;
    private final ScheduledExecutorService scheduler;

    public ExecutorRuntime(
            String executorId,
            List<Integer> slots,
            SlotOwnershipRepository ownership,
            ExecutorRegistry executors,
            TriggerLoop triggerLoop,
            RecoverLoop recoverLoop,
            ScheduledExecutorService scheduler
    ) {
        this.executorId = Objects.requireNonNull(executorId);
        if (executorId.isBlank()) {
            throw new IllegalArgumentException("executorId must not be blank");
        }
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("at least one slot is required");
        }
        this.slots = List.copyOf(slots);
        this.ownership = Objects.requireNonNull(ownership);
        this.executors = Objects.requireNonNull(executors);
        this.triggerLoop = Objects.requireNonNull(triggerLoop);
        this.recoverLoop = Objects.requireNonNull(recoverLoop);
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    public void start() {
        for (int slotNo : slots) {
            ownership.claim(slotNo, executorId);
        }
        scheduler.scheduleAtFixedRate(this::poll, 0, 1, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(recoverLoop::tick, 2, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> executors.heartbeat(executorId), 5, 10, TimeUnit.SECONDS);
    }

    private void poll() {
        for (int slotNo : slots) {
            try {
                triggerLoop.tick(slotNo);
            } catch (RuntimeException ex) {
                System.getLogger(ExecutorRuntime.class.getName())
                        .log(System.Logger.Level.ERROR, "poll slot " + slotNo + " failed", ex);
            }
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
