package io.taskx.core.fake;

import io.taskx.core.domain.SlotOwnership;
import io.taskx.core.store.SlotOccupiedException;
import io.taskx.core.store.SlotOwnershipRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySlotOwnershipRepository implements SlotOwnershipRepository {

    private final ConcurrentHashMap<Integer, String> owners = new ConcurrentHashMap<>();

    @Override
    public Optional<String> findOwner(int slotNo) {
        return Optional.ofNullable(owners.get(slotNo));
    }

    @Override
    public List<SlotOwnership> listAll() {
        return owners.entrySet().stream()
                .map(entry -> new SlotOwnership(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public void claim(int slotNo, String executorId) {
        String existing = owners.putIfAbsent(slotNo, executorId);
        if (existing == null || existing.equals(executorId)) {
            owners.put(slotNo, executorId);
            return;
        }
        throw new SlotOccupiedException(slotNo, existing);
    }

    @Override
    public void reassign(int slotNo, String executorId) {
        owners.put(slotNo, executorId);
    }
}
