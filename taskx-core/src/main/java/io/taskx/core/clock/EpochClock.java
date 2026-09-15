package io.taskx.core.clock;

/**
 * Unix-epoch seconds. Production uses Redis {@code TIME}; tests use a fixed value.
 */
@FunctionalInterface
public interface EpochClock {

    long nowEpochSecond();

    static EpochClock fixed(long epochSecond) {
        return () -> epochSecond;
    }
}
