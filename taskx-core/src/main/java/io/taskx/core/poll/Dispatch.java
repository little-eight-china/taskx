package io.taskx.core.poll;

import io.taskx.core.clock.EpochClock;
import io.taskx.core.domain.Execution;
import io.taskx.core.domain.ExecutionStatus;
import io.taskx.core.domain.Task;
import io.taskx.core.domain.TaskTarget;
import io.taskx.core.handler.TaskHandler;
import io.taskx.core.handler.TaskHandlerRegistry;
import io.taskx.core.schedule.NextFireCalculator;
import io.taskx.core.slot.SlotConfig;
import io.taskx.core.store.ExecutionRepository;
import io.taskx.core.store.TaskRepository;

import java.lang.System.Logger;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Async insert + handler. Slot lock is not held while the handler runs.
 */
public final class Dispatch {

    private static final Logger LOG = System.getLogger(Dispatch.class.getName());

    private final String executorId;
    private final SlotConfig slots;
    private final EpochClock clock;
    private final NextFireCalculator calculator;
    private final TaskRepository tasks;
    private final ExecutionRepository executions;
    private final TaskHandlerRegistry handlers;
    private final WorkflowLauncher workflowLauncher;
    private final Executor workers;
    private final TerminalScoreWriter terminalScores;

    public Dispatch(
            String executorId,
            SlotConfig slots,
            EpochClock clock,
            NextFireCalculator calculator,
            TaskRepository tasks,
            ExecutionRepository executions,
            TaskHandlerRegistry handlers,
            Executor workers,
            TerminalScoreWriter terminalScores
    ) {
        this(
                executorId,
                slots,
                clock,
                calculator,
                tasks,
                executions,
                handlers,
                null,
                workers,
                terminalScores
        );
    }

    public Dispatch(
            String executorId,
            SlotConfig slots,
            EpochClock clock,
            NextFireCalculator calculator,
            TaskRepository tasks,
            ExecutionRepository executions,
            TaskHandlerRegistry handlers,
            WorkflowLauncher workflowLauncher,
            Executor workers,
            TerminalScoreWriter terminalScores
    ) {
        this.executorId = Objects.requireNonNull(executorId);
        this.slots = Objects.requireNonNull(slots);
        this.clock = Objects.requireNonNull(clock);
        this.calculator = Objects.requireNonNull(calculator);
        this.tasks = Objects.requireNonNull(tasks);
        this.executions = Objects.requireNonNull(executions);
        this.handlers = Objects.requireNonNull(handlers);
        this.workflowLauncher = workflowLauncher;
        this.workers = Objects.requireNonNull(workers);
        this.terminalScores = Objects.requireNonNull(terminalScores);
    }

    public void enqueue(Task task, long fireTime) {
        try {
            workers.execute(() -> runClaimed(task, fireTime));
        } catch (RejectedExecutionException rejected) {
            markFailed(task.id(), fireTime);
        }
    }

    public void recover(Execution pending) {
        runRow(pending);
    }

    private void runClaimed(Task task, long fireTime) {
        Optional<Execution> inserted = executions.insertPending(Execution.pending(task.id(), fireTime, executorId));
        if (inserted.isEmpty()) {
            return;
        }
        runRow(inserted.get());
    }

    private void markFailed(String taskId, long fireTime) {
        Optional<Execution> inserted = executions.insertPending(Execution.pending(taskId, fireTime, executorId));
        inserted.ifPresent(row -> executions.casStatus(row.id(), ExecutionStatus.PENDING, ExecutionStatus.FAILED));
        LOG.log(Logger.Level.WARNING, "handler pool rejected {0} fireTime={1}", taskId, fireTime);
    }

    private void runRow(Execution row) {
        if (!executions.casStatus(row.id(), ExecutionStatus.PENDING, ExecutionStatus.RUNNING)) {
            return;
        }
        Execution running = row.withStatus(ExecutionStatus.RUNNING);
        Optional<Task> task = tasks.findById(row.taskId());
        ExecutionStatus terminal = ExecutionStatus.SUCCESS;
        try {
            if (task.isEmpty()) {
                throw new IllegalStateException("task missing: " + row.taskId());
            }
            Task loaded = task.get();
            if (loaded.target() instanceof TaskTarget.Handler handlerTarget) {
                Optional<TaskHandler> handler = handlers.find(handlerTarget.name());
                if (handler.isEmpty()) {
                    throw new IllegalStateException("no handler registered: " + handlerTarget.name());
                }
                handler.get().handle(loaded, running);
            } else if (loaded.target() instanceof TaskTarget.Workflow workflowTarget) {
                if (workflowLauncher == null) {
                    throw new IllegalStateException("workflow runtime is not configured");
                }
                workflowLauncher.launch(loaded, workflowTarget, running);
                // Workflow completion owns the root execution terminal state.
                return;
            }
        } catch (Exception ex) {
            terminal = ExecutionStatus.FAILED;
            LOG.log(Logger.Level.ERROR, "handler failed taskId=" + row.taskId(), ex);
        }
        executions.casStatus(row.id(), ExecutionStatus.RUNNING, terminal);
        if (task.isPresent()) {
            writeFixedDelay(task.get(), terminal);
        }
    }

    private void writeFixedDelay(Task task, ExecutionStatus terminal) {
        if (terminal != ExecutionStatus.SUCCESS && terminal != ExecutionStatus.FAILED) {
            return;
        }
        OptionalLong next = calculator.nextOnTerminal(task.trigger(), clock.nowEpochSecond());
        if (next.isEmpty()) {
            return;
        }
        terminalScores.write(task.slot(slots), task.id(), next);
    }
}
