package io.taskx.core.poll;

import io.taskx.common.lock.DistributedLock;
import io.taskx.common.lock.LockLease;
import io.taskx.core.clock.EpochClock;
import io.taskx.core.domain.Task;
import io.taskx.core.schedule.NextFireCalculator;
import io.taskx.core.slot.SlotRedisKeys;
import io.taskx.core.store.DueMember;
import io.taskx.core.store.SlotOwnershipRepository;
import io.taskx.core.store.TaskRepository;
import io.taskx.core.store.TriggerIndex;

import java.lang.System.Logger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One round on one slot: lock → ownership → due members → sync ZADD/ZREM → async dispatch → unlock.
 */
public final class TriggerLoop {

    public static final int DEFAULT_DUE_LIMIT = 100;

    private static final Logger LOG = System.getLogger(TriggerLoop.class.getName());

    private final String executorId;
    private final DistributedLock lock;
    private final SlotOwnershipRepository ownership;
    private final TriggerIndex triggerIndex;
    private final TaskRepository tasks;
    private final EpochClock clock;
    private final NextFireCalculator calculator;
    private final Dispatch dispatch;
    private final int dueLimit;

    public TriggerLoop(
            String executorId,
            DistributedLock lock,
            SlotOwnershipRepository ownership,
            TriggerIndex triggerIndex,
            TaskRepository tasks,
            EpochClock clock,
            NextFireCalculator calculator,
            Dispatch dispatch,
            int dueLimit
    ) {
        this.executorId = Objects.requireNonNull(executorId);
        this.lock = Objects.requireNonNull(lock);
        this.ownership = Objects.requireNonNull(ownership);
        this.triggerIndex = Objects.requireNonNull(triggerIndex);
        this.tasks = Objects.requireNonNull(tasks);
        this.clock = Objects.requireNonNull(clock);
        this.calculator = Objects.requireNonNull(calculator);
        this.dispatch = Objects.requireNonNull(dispatch);
        if (dueLimit <= 0) {
            throw new IllegalArgumentException("dueLimit must be > 0");
        }
        this.dueLimit = dueLimit;
    }

    public void tick(int slotNo) {
        Optional<LockLease> lease = lock.tryLock(SlotRedisKeys.lock(slotNo));
        if (lease.isEmpty()) {
            return;
        }
        try (LockLease held = lease.get()) {
            Optional<String> owner = ownership.findOwner(slotNo);
            if (owner.isEmpty() || !executorId.equals(owner.get())) {
                LOG.log(Logger.Level.DEBUG, "skip slot {0}, owner={1}", slotNo, owner.orElse("-"));
                return;
            }
            long now = clock.nowEpochSecond();
            List<DueMember> due = triggerIndex.rangeDue(slotNo, now, dueLimit);
            for (DueMember member : due) {
                claim(slotNo, member, now);
            }
        }
    }

    private void claim(int slotNo, DueMember member, long now) {
        Optional<Task> found = tasks.findById(member.taskId());
        if (found.isEmpty() || !found.get().enabled()) {
            triggerIndex.remove(slotNo, member.taskId());
            return;
        }
        Task task = found.get();
        triggerIndex.replace(slotNo, task.id(), calculator.nextOnClaim(task.trigger(), now));
        dispatch.enqueue(task, member.scoreEpochSecond());
    }
}
