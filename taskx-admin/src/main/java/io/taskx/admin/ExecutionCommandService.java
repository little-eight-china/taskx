package io.taskx.admin;

import io.taskx.core.domain.Execution;
import io.taskx.core.domain.ExecutionStatus;
import io.taskx.core.domain.TaskTarget;
import io.taskx.core.store.ExecutionRepository;
import io.taskx.core.store.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class ExecutionCommandService {

    private final ExecutionRepository executions;
    private final TaskRepository tasks;

    public ExecutionCommandService(ExecutionRepository executions, TaskRepository tasks) {
        this.executions = Objects.requireNonNull(executions);
        this.tasks = Objects.requireNonNull(tasks);
    }

    public Execution cancel(long id) {
        Execution row = require(id);
        rejectWorkflowCommand(row, "cancel");
        if (!row.status().canTransitionTo(ExecutionStatus.CANCELLED)) {
            throw new IllegalStateException("cannot cancel status " + row.status());
        }
        if (!executions.casStatus(id, row.status(), ExecutionStatus.CANCELLED)) {
            throw new IllegalStateException("execution " + id + " changed concurrently");
        }
        return require(id);
    }

    public Execution requeue(long id) {
        Execution row = require(id);
        rejectWorkflowCommand(row, "requeue");
        if (row.status() != ExecutionStatus.RUNNING && row.status() != ExecutionStatus.FAILED) {
            throw new IllegalStateException("requeue requires RUNNING or FAILED, was " + row.status());
        }
        if (!executions.casStatus(id, row.status(), ExecutionStatus.PENDING)) {
            throw new IllegalStateException("execution " + id + " changed concurrently");
        }
        return require(id);
    }

    private Execution require(long id) {
        return executions.findById(id).orElseThrow(() -> new NoSuchElementException("execution not found: " + id));
    }

    private void rejectWorkflowCommand(Execution row, String command) {
        tasks.findById(row.taskId()).ifPresent(task -> {
            if (task.target() instanceof TaskTarget.Workflow) {
                throw new IllegalStateException(
                        command + " for workflow root executions requires workflow-instance commands");
            }
        });
    }
}
