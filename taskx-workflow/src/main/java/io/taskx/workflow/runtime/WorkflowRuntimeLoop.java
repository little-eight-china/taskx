package io.taskx.workflow.runtime;

import io.taskx.core.store.SlotOwnershipRepository;

import java.lang.System.Logger;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WorkflowRuntimeLoop implements AutoCloseable {

    private static final Logger LOG = System.getLogger(WorkflowRuntimeLoop.class.getName());

    private final String executorId;
    private final List<Integer> slots;
    private final List<String> workerGroups;
    private final SlotOwnershipRepository ownership;
    private final WorkflowEngine engine;
    private final WorkflowOutboxPublisher outbox;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean started = new AtomicBoolean();

    public WorkflowRuntimeLoop(
            String executorId,
            List<Integer> slots,
            List<String> workerGroups,
            SlotOwnershipRepository ownership,
            WorkflowEngine engine,
            WorkflowOutboxPublisher outbox,
            ScheduledExecutorService scheduler
    ) {
        this.executorId = Objects.requireNonNull(executorId, "executorId");
        this.slots = List.copyOf(slots);
        this.workerGroups = List.copyOf(workerGroups);
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        scheduler.scheduleWithFixedDelay(this::publish, 0, 500, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(this::poll, 0, 1, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::recover, 5, 5, TimeUnit.SECONDS);
    }

    private void publish() {
        safely("publish workflow outbox", () -> outbox.publishBatch(100));
    }

    private void poll() {
        for (int slotNo : slots) {
            if (!ownership.findOwner(slotNo).filter(executorId::equals).isPresent()) {
                continue;
            }
            for (String workerGroup : workerGroups) {
                safely(
                        "poll workflow group=" + workerGroup + " slot=" + slotNo,
                        () -> engine.tick(workerGroup, slotNo, executorId, 100)
                );
            }
        }
    }

    private void recover() {
        safely(
                "recover workflow leases",
                () -> engine.recoverExpiredLeases(100)
        );
    }

    private static void safely(String action, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException ex) {
            LOG.log(Logger.Level.ERROR, action + " failed", ex);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
