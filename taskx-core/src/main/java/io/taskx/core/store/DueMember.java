package io.taskx.core.store;

/**
 * One due ZSET member. {@code scoreEpochSecond} is the claimed fireTime.
 */
public record DueMember(String taskId, long scoreEpochSecond) {
}
