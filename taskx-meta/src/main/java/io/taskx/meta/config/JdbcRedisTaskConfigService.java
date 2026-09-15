package io.taskx.meta.config;

import io.taskx.common.lock.DistributedLock;
import io.taskx.common.lock.LockLease;
import io.taskx.core.clock.EpochClock;
import io.taskx.core.config.TaskConfigService;
import io.taskx.core.domain.Task;
import io.taskx.core.schedule.NextFireCalculator;
import io.taskx.core.slot.SlotConfig;
import io.taskx.core.slot.SlotRedisKeys;
import io.taskx.core.store.SlotBusyException;
import io.taskx.core.store.TriggerIndex;
import io.taskx.meta.MetaException;
import io.taskx.meta.jdbc.JdbcTaskRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Slot lock → uncommitted MySQL → Redis ZADD/ZREM → COMMIT. Redis failure rolls back the row.
 */
public final class JdbcRedisTaskConfigService implements TaskConfigService {

    private final DataSource dataSource;
    private final JdbcTaskRepository tasks;
    private final DistributedLock lock;
    private final TriggerIndex triggerIndex;
    private final EpochClock clock;
    private final NextFireCalculator calculator;
    private final SlotConfig slots;

    public JdbcRedisTaskConfigService(
            DataSource dataSource,
            JdbcTaskRepository tasks,
            DistributedLock lock,
            TriggerIndex triggerIndex,
            EpochClock clock,
            NextFireCalculator calculator,
            SlotConfig slots
    ) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.tasks = Objects.requireNonNull(tasks);
        this.lock = Objects.requireNonNull(lock);
        this.triggerIndex = Objects.requireNonNull(triggerIndex);
        this.clock = Objects.requireNonNull(clock);
        this.calculator = Objects.requireNonNull(calculator);
        this.slots = Objects.requireNonNull(slots);
    }

    @Override
    public void save(Task task) {
        int slotNo = task.slot(slots);
        withSlotLock(slotNo, () -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    tasks.save(connection, task);
                    syncIndex(slotNo, task);
                    connection.commit();
                } catch (RuntimeException | SQLException ex) {
                    connection.rollback();
                    throw wrap(ex);
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        });
    }

    @Override
    public void disable(String taskId) {
        save(requireTask(taskId).withEnabled(false));
    }

    @Override
    public void enable(String taskId) {
        save(requireTask(taskId).withEnabled(true));
    }

    @Override
    public void delete(String taskId) {
        int slotNo = slots.slotOf(taskId);
        withSlotLock(slotNo, () -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    tasks.delete(connection, taskId);
                    triggerIndex.remove(slotNo, taskId);
                    connection.commit();
                } catch (RuntimeException | SQLException ex) {
                    connection.rollback();
                    throw wrap(ex);
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        });
    }

    private void syncIndex(int slotNo, Task task) {
        if (!task.enabled()) {
            triggerIndex.remove(slotNo, task.id());
            return;
        }
        triggerIndex.replace(slotNo, task.id(), calculator.firstFire(task.trigger(), clock.nowEpochSecond()));
    }

    private Task requireTask(String taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new NoSuchElementException("task not found: " + taskId));
    }

    private void withSlotLock(int slotNo, SqlWork work) {
        Optional<LockLease> lease = lock.tryLock(SlotRedisKeys.lock(slotNo));
        if (lease.isEmpty()) {
            throw new SlotBusyException(slotNo);
        }
        try (LockLease held = lease.get()) {
            work.run();
        } catch (SQLException ex) {
            throw new MetaException("config tx slot " + slotNo, ex);
        }
    }

    private static RuntimeException wrap(Exception ex) {
        if (ex instanceof RuntimeException runtime) {
            return runtime;
        }
        return new MetaException("config write failed", ex);
    }

    @FunctionalInterface
    private interface SqlWork {
        void run() throws SQLException;
    }
}
