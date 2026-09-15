package io.taskx.core.store;

import java.util.List;

public interface ExecutorRegistry {

    void heartbeat(String executorId);

    List<ExecutorView> list();
}
