package io.taskx.core.store;

import io.taskx.core.domain.Execution;
import io.taskx.core.domain.ExecutionStatus;

import java.util.List;
import java.util.Optional;

/**
 * Execution-row store. Unique key is {@code (taskId, scheduledFireTime)}; execution right is CAS on status.
 */
public interface ExecutionRepository {

    /**
     * Insert a {@code PENDING} row. Empty if {@code (taskId, scheduledFireTime)} already exists.
     */
    Optional<Execution> insertPending(Execution execution);

    /**
     * Compare-and-set status by primary key. {@code false} if the row is missing or status mismatches.
     */
    boolean casStatus(long id, ExecutionStatus expected, ExecutionStatus next);

    List<Execution> findPendingByExecutorId(String executorId);

    Optional<Execution> findById(long id);

    List<Execution> list(String taskId, ExecutionStatus status, int limit);
}
