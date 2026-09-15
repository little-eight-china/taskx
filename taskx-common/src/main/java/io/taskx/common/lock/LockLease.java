package io.taskx.common.lock;

/**
 * Held lock. {@link #close()} releases it and stops the watchdog.
 */
public interface LockLease extends AutoCloseable {

    String key();

    boolean isHeld();

    @Override
    void close();
}
