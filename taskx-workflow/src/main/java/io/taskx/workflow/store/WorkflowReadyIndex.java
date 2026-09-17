package io.taskx.workflow.store;

import java.time.Instant;
import java.util.List;

/**
 * Rebuildable Redis acceleration index. Runtime correctness remains in MySQL.
 */
public interface WorkflowReadyIndex {

    void add(String workerGroup, int slotNo, String activationId, Instant availableAt);

    void remove(String workerGroup, int slotNo, String activationId);

    List<String> rangeDue(String workerGroup, int slotNo, Instant now, int limit);
}
