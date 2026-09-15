package io.taskx.core.store;

import io.taskx.core.domain.SlotOwnership;

import java.util.List;
import java.util.Optional;

public interface SlotOwnershipRepository {

    Optional<String> findOwner(int slotNo);

    List<SlotOwnership> listAll();

    /**
     * Bind {@code slotNo} to {@code executorId}. Fails if another executor already owns it.
     */
    void claim(int slotNo, String executorId);

    /**
     * Admin reassignment: overwrite owner after holding the slot lock.
     */
    void reassign(int slotNo, String executorId);
}
