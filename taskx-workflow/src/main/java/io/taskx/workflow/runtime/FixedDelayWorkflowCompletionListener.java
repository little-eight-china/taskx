package io.taskx.workflow.runtime;

import io.taskx.core.clock.EpochClock;
import io.taskx.core.domain.Task;
import io.taskx.core.poll.TerminalScoreWriter;
import io.taskx.core.schedule.NextFireCalculator;
import io.taskx.core.slot.SlotConfig;
import io.taskx.core.store.ExecutionRepository;
import io.taskx.core.store.TaskRepository;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Mirrors direct Dispatch fixed-delay behavior after the workflow owns the root execution.
 */
public final class FixedDelayWorkflowCompletionListener implements WorkflowCompletionListener {

    private final ExecutionRepository executions;
    private final TaskRepository tasks;
    private final EpochClock clock;
    private final NextFireCalculator calculator;
    private final SlotConfig slots;
    private final TerminalScoreWriter terminalScores;

    public FixedDelayWorkflowCompletionListener(
            ExecutionRepository executions,
            TaskRepository tasks,
            EpochClock clock,
            NextFireCalculator calculator,
            SlotConfig slots,
            TerminalScoreWriter terminalScores
    ) {
        this.executions = Objects.requireNonNull(executions, "executions");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.slots = Objects.requireNonNull(slots, "slots");
        this.terminalScores = Objects.requireNonNull(terminalScores, "terminalScores");
    }

    @Override
    public void onTerminal(long rootExecutionId, WorkflowInstanceStatus status) {
        if (status != WorkflowInstanceStatus.SUCCESS && status != WorkflowInstanceStatus.FAILED) {
            return;
        }
        executions.findById(rootExecutionId)
                .flatMap(execution -> tasks.findById(execution.taskId()))
                .ifPresent(this::writeNext);
    }

    private void writeNext(Task task) {
        OptionalLong next = calculator.nextOnTerminal(task.trigger(), clock.nowEpochSecond());
        if (next.isPresent()) {
            terminalScores.write(task.slot(slots), task.id(), next);
        }
    }
}
