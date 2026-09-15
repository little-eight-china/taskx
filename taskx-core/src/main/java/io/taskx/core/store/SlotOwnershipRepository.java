package io.taskx.core.store;

import java.util.Optional;

public interface SlotOwnershipRepository {

    Optional<String> findOwner(int slotNo);
}
