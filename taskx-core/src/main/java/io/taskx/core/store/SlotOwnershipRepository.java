package io.taskx.core.store;

import java.util.Optional;

public interface SlotOwnershipRepository {

    Optional<String> findOwner(int slotNo);

    /**
     * Bind {@code slotNo} to {@code executorId}. Fails if another executor already owns it.
     */
    void claim(int slotNo, String executorId);
}
