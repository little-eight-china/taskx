package io.taskx.meta.redis;

import io.taskx.common.lock.DistributedLock;
import io.taskx.common.lock.LockLease;
import io.taskx.common.lock.LockSettings;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redisson lock with watchdog (leaseTime = -1). {@link LockSettings#ttl()} maps to lockWatchdogTimeout.
 */
public final class RedissonDistributedLock implements DistributedLock {

    private final RedissonClient redisson;

    public RedissonDistributedLock(RedissonClient redisson, LockSettings settings) {
        this.redisson = Objects.requireNonNull(redisson);
        Objects.requireNonNull(settings);
    }

    @Override
    public Optional<LockLease> tryLock(String key) {
        RLock lock = redisson.getLock(key);
        try {
            if (!lock.tryLock()) {
                return Optional.empty();
            }
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
        return Optional.of(new RedissonLease(lock));
    }

    private static final class RedissonLease implements LockLease {

        private final RLock lock;
        private final AtomicBoolean open = new AtomicBoolean(true);

        private RedissonLease(RLock lock) {
            this.lock = lock;
        }

        @Override
        public String key() {
            return lock.getName();
        }

        @Override
        public boolean isHeld() {
            return open.get() && lock.isHeldByCurrentThread();
        }

        @Override
        public void close() {
            if (open.compareAndSet(true, false) && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
