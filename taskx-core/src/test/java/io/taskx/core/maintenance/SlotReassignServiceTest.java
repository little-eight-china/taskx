package io.taskx.core.maintenance;

import io.taskx.common.lock.InMemoryLock;
import io.taskx.core.fake.InMemorySlotOwnershipRepository;
import io.taskx.core.slot.SlotConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlotReassignServiceTest {

    @Test
    void reassignOverwritesOwnerWhileHoldingLock() {
        InMemoryLock lock = new InMemoryLock();
        InMemorySlotOwnershipRepository ownership = new InMemorySlotOwnershipRepository();
        ownership.claim(0, "ex-old");
        SlotConfig slots = new SlotConfig(2);

        new SlotReassignService(lock, ownership, slots).reassign(0, "ex-new");

        assertEquals("ex-new", ownership.findOwner(0).orElseThrow());
    }

    @Test
    void reassignRejectsOutOfRangeSlot() {
        SlotReassignService service = new SlotReassignService(
                new InMemoryLock(), new InMemorySlotOwnershipRepository(), new SlotConfig(2));
        assertThrows(IllegalArgumentException.class, () -> service.reassign(2, "ex-1"));
    }
}
