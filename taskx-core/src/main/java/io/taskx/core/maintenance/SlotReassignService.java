package io.taskx.core.maintenance;

import io.taskx.common.lock.DistributedLock;
import io.taskx.common.lock.LockLease;
import io.taskx.core.slot.SlotConfig;
import io.taskx.core.slot.SlotRedisKeys;
import io.taskx.core.store.SlotBusyException;
import io.taskx.core.store.SlotOwnershipRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Overwrite slot ownership while holding the same lock executors use to poll.
 */
public final class SlotReassignService {

    private final DistributedLock lock;
    private final SlotOwnershipRepository ownership;
    private final SlotConfig slots;

    public SlotReassignService(DistributedLock lock, SlotOwnershipRepository ownership, SlotConfig slots) {
        this.lock = Objects.requireNonNull(lock);
        this.ownership = Objects.requireNonNull(ownership);
        this.slots = Objects.requireNonNull(slots);
    }

    public void reassign(int slotNo, String executorId) {
        if (slotNo < 0 || slotNo >= slots.slotCount()) {
            throw new IllegalArgumentException("slotNo must be in 0.." + (slots.slotCount() - 1));
        }
        if (executorId == null || executorId.isBlank()) {
            throw new IllegalArgumentException("executorId must not be blank");
        }
        Optional<LockLease> lease = lock.tryLock(SlotRedisKeys.lock(slotNo));
        if (lease.isEmpty()) {
            throw new SlotBusyException(slotNo);
        }
        try (LockLease held = lease.get()) {
            ownership.reassign(slotNo, executorId);
        }
    }
}
