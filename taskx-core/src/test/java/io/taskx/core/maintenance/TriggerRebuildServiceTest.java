package io.taskx.core.maintenance;

import io.taskx.common.lock.InMemoryLock;
import io.taskx.core.clock.EpochClock;
import io.taskx.core.domain.Task;
import io.taskx.core.domain.Trigger;
import io.taskx.core.fake.InMemoryTaskRepository;
import io.taskx.core.fake.InMemoryTriggerIndex;
import io.taskx.core.schedule.NextFireCalculator;
import io.taskx.core.slot.SlotConfig;
import io.taskx.core.slot.SlotRedisKeys;
import io.taskx.core.store.SlotBusyException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerRebuildServiceTest {

    @Test
    void rebuildClearsThenAddsEnabledTasksFromNow() {
        InMemoryLock lock = new InMemoryLock();
        InMemoryTriggerIndex index = new InMemoryTriggerIndex();
        InMemoryTaskRepository tasks = new InMemoryTaskRepository();
        SlotConfig slots = new SlotConfig(1);
        long now = 1_000L;
        tasks.save(new Task("a", "demo", null, true, new Trigger.FixedRate(60), null));
        tasks.save(new Task("b", "demo", null, false, new Trigger.FixedRate(60), null));
        index.add(0, "stale", 1L);

        int enabled = new TriggerRebuildService(
                lock, index, tasks, EpochClock.fixed(now), new NextFireCalculator(), slots
        ).rebuild();

        assertEquals(1, enabled);
        assertNull(index.score(0, "stale"));
        assertEquals(now + 60, index.score(0, "a"));
        assertNull(index.score(0, "b"));
    }

    @Test
    void rebuildFailsWhenASlotIsLocked() {
        InMemoryLock lock = new InMemoryLock();
        SlotConfig slots = new SlotConfig(2);
        lock.tryLock(SlotRedisKeys.lock(1));

        TriggerRebuildService service = new TriggerRebuildService(
                lock,
                new InMemoryTriggerIndex(),
                new InMemoryTaskRepository(),
                EpochClock.fixed(1L),
                new NextFireCalculator(),
                slots
        );

        SlotBusyException ex = assertThrows(SlotBusyException.class, service::rebuild);
        assertEquals(1, ex.slotNo());
        assertTrue(lock.tryLock(SlotRedisKeys.lock(0)).isPresent());
    }
}
