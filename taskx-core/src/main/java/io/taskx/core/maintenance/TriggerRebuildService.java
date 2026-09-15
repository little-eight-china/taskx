package io.taskx.core.maintenance;

import io.taskx.common.lock.DistributedLock;
import io.taskx.common.lock.LockLease;
import io.taskx.core.clock.EpochClock;
import io.taskx.core.domain.Task;
import io.taskx.core.schedule.NextFireCalculator;
import io.taskx.core.slot.SlotConfig;
import io.taskx.core.slot.SlotRedisKeys;
import io.taskx.core.store.SlotBusyException;
import io.taskx.core.store.TaskRepository;
import io.taskx.core.store.TriggerIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stop executors first. Locks every slot, clears ZSETs, then re-adds enabled tasks from now.
 */
public final class TriggerRebuildService {

    private final DistributedLock lock;
    private final TriggerIndex triggerIndex;
    private final TaskRepository tasks;
    private final EpochClock clock;
    private final NextFireCalculator calculator;
    private final SlotConfig slots;

    public TriggerRebuildService(
            DistributedLock lock,
            TriggerIndex triggerIndex,
            TaskRepository tasks,
            EpochClock clock,
            NextFireCalculator calculator,
            SlotConfig slots
    ) {
        this.lock = Objects.requireNonNull(lock);
        this.triggerIndex = Objects.requireNonNull(triggerIndex);
        this.tasks = Objects.requireNonNull(tasks);
        this.clock = Objects.requireNonNull(clock);
        this.calculator = Objects.requireNonNull(calculator);
        this.slots = Objects.requireNonNull(slots);
    }

    public int rebuild() {
        List<LockLease> leases = new ArrayList<>();
        try {
            for (int slotNo = 0; slotNo < slots.slotCount(); slotNo++) {
                Optional<LockLease> lease = lock.tryLock(SlotRedisKeys.lock(slotNo));
                if (lease.isEmpty()) {
                    throw new SlotBusyException(slotNo);
                }
                leases.add(lease.get());
            }
            for (int slotNo = 0; slotNo < slots.slotCount(); slotNo++) {
                triggerIndex.clear(slotNo);
            }
            long now = clock.nowEpochSecond();
            int enabled = 0;
            for (Task task : tasks.findAll()) {
                if (!task.enabled()) {
                    continue;
                }
                triggerIndex.replace(task.slot(slots), task.id(), calculator.firstFire(task.trigger(), now));
                enabled++;
            }
            return enabled;
        } finally {
            for (int i = leases.size() - 1; i >= 0; i--) {
                leases.get(i).close();
            }
        }
    }
}
