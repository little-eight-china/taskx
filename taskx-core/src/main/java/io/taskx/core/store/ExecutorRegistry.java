package io.taskx.core.store;

public interface ExecutorRegistry {

    void heartbeat(String executorId);
}
