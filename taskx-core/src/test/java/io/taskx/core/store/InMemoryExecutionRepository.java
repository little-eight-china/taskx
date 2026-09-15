package io.taskx.core.store;

import io.taskx.core.domain.Execution;
import io.taskx.core.domain.ExecutionStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process store used to lock unique-key and CAS contracts in tests.
 */
public final class InMemoryExecutionRepository implements ExecutionRepository {

    private final AtomicLong ids = new AtomicLong(1);
    private final ConcurrentHashMap<Long, Execution> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> unique = new ConcurrentHashMap<>();

    @Override
    public Optional<Execution> insertPending(Execution execution) {
        Objects.requireNonNull(execution, "execution");
        if (execution.status() != ExecutionStatus.PENDING) {
            throw new IllegalArgumentException("insertPending requires PENDING status");
        }
        String key = uniqueKey(execution.taskId(), execution.scheduledFireTime());
        long id = ids.getAndIncrement();
        Execution stored = execution.withId(id);
        Long existing = unique.putIfAbsent(key, id);
        if (existing != null) {
            return Optional.empty();
        }
        byId.put(id, stored);
        return Optional.of(stored);
    }

    @Override
    public boolean casStatus(long id, ExecutionStatus expected, ExecutionStatus next) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(next, "next");
        while (true) {
            Execution current = byId.get(id);
            if (current == null || current.status() != expected) {
                return false;
            }
            if (byId.replace(id, current, current.withStatus(next))) {
                return true;
            }
        }
    }

    @Override
    public List<Execution> findPendingByExecutorId(String executorId) {
        if (executorId == null || executorId.isBlank()) {
            throw new IllegalArgumentException("executorId must not be blank");
        }
        return byId.values().stream()
                .filter(row -> row.status() == ExecutionStatus.PENDING)
                .filter(row -> executorId.equals(row.executorId()))
                .toList();
    }

    public Optional<Execution> findById(long id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<Execution> all() {
        return List.copyOf(byId.values());
    }

    private static String uniqueKey(String taskId, long scheduledFireTime) {
        return taskId + '\0' + scheduledFireTime;
    }
}
