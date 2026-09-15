package io.taskx.common.lock;

import java.util.Optional;

/**
 * Non-blocking named lock. Redis/Redisson implements this; {@link InMemoryLock} is the test fake.
 */
public interface DistributedLock {

    Optional<LockLease> tryLock(String key);
}
