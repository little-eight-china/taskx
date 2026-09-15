package io.taskx.core.poll;

import io.taskx.common.lock.DistributedLock;
import io.taskx.common.lock.LockLease;
import io.taskx.core.slot.SlotRedisKeys;
import io.taskx.core.store.TriggerIndex;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Writes FIXED_DELAY next score after the poller has already released the slot lock.
 */
public final class LockingTerminalScoreWriter implements TerminalScoreWriter {

    private final DistributedLock lock;
    private final TriggerIndex triggerIndex;

    public LockingTerminalScoreWriter(DistributedLock lock, TriggerIndex triggerIndex) {
        this.lock = Objects.requireNonNull(lock);
        this.triggerIndex = Objects.requireNonNull(triggerIndex);
    }

    @Override
    public void write(int slotNo, String taskId, OptionalLong nextScore) {
        for (int attempt = 0; attempt < 3; attempt++) {
            Optional<LockLease> lease = lock.tryLock(SlotRedisKeys.lock(slotNo));
            if (lease.isEmpty()) {
                continue;
            }
            try (LockLease held = lease.get()) {
                triggerIndex.replace(slotNo, taskId, nextScore);
                return;
            }
        }
    }
}
