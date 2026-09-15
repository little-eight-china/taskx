package io.taskx.core.slot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlotHasherTest {

    @Test
    void knownVectorHello() {
        assertEquals(6, SlotHasher.slotOf("hello", 32));
        assertEquals(6, SlotConfig.DEFAULT.slotOf("hello"));
    }

    @Test
    void knownVectorTask1() {
        assertEquals(15, SlotHasher.slotOf("task-1", 32));
    }

    @Test
    void sameTaskAlwaysSameSlot() {
        assertEquals(SlotHasher.slotOf("stable-id", 32), SlotHasher.slotOf("stable-id", 32));
    }

    @Test
    void rejectsBlankAndNonPositiveN() {
        assertThrows(IllegalArgumentException.class, () -> SlotHasher.slotOf(" ", 32));
        assertThrows(IllegalArgumentException.class, () -> SlotHasher.slotOf("a", 0));
        assertThrows(IllegalArgumentException.class, () -> new SlotConfig(0));
    }

    @Test
    void redisKeysFollowSlotConvention() {
        assertEquals("trigger:slot:3", SlotRedisKeys.trigger(3));
        assertEquals("lock:slot:3", SlotRedisKeys.lock(3));
        assertThrows(IllegalArgumentException.class, () -> SlotRedisKeys.lock(-1));
    }
}
