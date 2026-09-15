package io.taskx.admin;

import io.taskx.core.domain.Execution;
import io.taskx.core.domain.ExecutionStatus;
import io.taskx.core.store.ExecutionRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class ExecutionCommandService {

    private final ExecutionRepository executions;

    public ExecutionCommandService(ExecutionRepository executions) {
        this.executions = Objects.requireNonNull(executions);
    }

    public Execution cancel(long id) {
        Execution row = require(id);
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
}
