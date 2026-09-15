package io.taskx.common.lock;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryLockTest {

    @Test
    void tryLockIsExclusivePerKey() {
        InMemoryLock lock = new InMemoryLock();
        LockLease lease = lock.tryLock("lock:slot:3").orElseThrow();
        assertEquals("lock:slot:3", lease.key());
        assertTrue(lease.isHeld());
        assertTrue(lock.tryLock("lock:slot:3").isEmpty());
        assertTrue(lock.tryLock("lock:slot:4").isPresent());
        lease.close();
        assertFalse(lease.isHeld());
        Optional<LockLease> again = lock.tryLock("lock:slot:3");
        assertTrue(again.isPresent());
        again.orElseThrow().close();
    }

    @Test
    void closeIsIdempotent() {
        InMemoryLock lock = new InMemoryLock();
        LockLease lease = lock.tryLock("k").orElseThrow();
        lease.close();
        lease.close();
        assertTrue(lock.tryLock("k").isPresent());
    }

    @Test
    void settingsRequireWatchdogShorterThanTtl() {
        LockSettings defaults = LockSettings.DEFAULT;
        assertEquals(Duration.ofSeconds(10), defaults.ttl());
        assertEquals(Duration.ofSeconds(3), defaults.watchdogInterval());
        assertThrows(IllegalArgumentException.class,
                () -> new LockSettings(Duration.ofSeconds(3), Duration.ofSeconds(3)));
        assertThrows(IllegalArgumentException.class, () -> new InMemoryLock().tryLock(" "));
    }
}
