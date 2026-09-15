package io.taskx.common.lock;

import java.time.Duration;
import java.util.Objects;

/**
 * TTL + watchdog for Redis-backed locks. Watchdog interval must be shorter than TTL.
 */
public record LockSettings(Duration ttl, Duration watchdogInterval) {

    public static final LockSettings DEFAULT =
            new LockSettings(Duration.ofSeconds(10), Duration.ofSeconds(3));

    public LockSettings {
        Objects.requireNonNull(ttl, "ttl");
        Objects.requireNonNull(watchdogInterval, "watchdogInterval");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (watchdogInterval.isZero() || watchdogInterval.isNegative()) {
            throw new IllegalArgumentException("watchdogInterval must be positive");
        }
        if (watchdogInterval.compareTo(ttl) >= 0) {
            throw new IllegalArgumentException("watchdogInterval must be shorter than ttl");
        }
    }
}
