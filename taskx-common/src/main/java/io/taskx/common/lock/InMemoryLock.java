package io.taskx.common.lock;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-local lock for unit tests. No TTL; {@code close()} is enough to release.
 */
public final class InMemoryLock implements DistributedLock {

    private final ConcurrentHashMap<String, InMemoryLease> held = new ConcurrentHashMap<>();

    @Override
    public Optional<LockLease> tryLock(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("lock key must not be blank");
        }
        InMemoryLease lease = new InMemoryLease(key);
        InMemoryLease existing = held.putIfAbsent(key, lease);
        if (existing != null) {
            return Optional.empty();
        }
        return Optional.of(lease);
    }

    private final class InMemoryLease implements LockLease {

        private final String key;
        private final AtomicBoolean open = new AtomicBoolean(true);

        private InMemoryLease(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public boolean isHeld() {
            return open.get();
        }

        @Override
        public void close() {
            if (open.compareAndSet(true, false)) {
                held.remove(key, this);
            }
        }
    }
}
