package io.taskx.core.fake;

import io.taskx.core.store.SlotOccupiedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemorySlotOwnershipRepositoryTest {

    @Test
    void secondExecutorCannotStealSlot() {
        InMemorySlotOwnershipRepository ownership = new InMemorySlotOwnershipRepository();
        ownership.claim(0, "ex-1");
        ownership.claim(0, "ex-1");
        SlotOccupiedException ex = assertThrows(SlotOccupiedException.class, () -> ownership.claim(0, "ex-2"));
        assertEquals("slot 0 is owned by ex-1", ex.getMessage());
    }
}
