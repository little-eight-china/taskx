package io.taskx.workflow.store;

import io.taskx.workflow.runtime.ClaimedActivation;
import io.taskx.workflow.runtime.OutboxEvent;
import io.taskx.workflow.runtime.WorkflowInstance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MySQL is the workflow source of truth. Every method that advances state is one database transaction.
 */
public interface WorkflowRuntimeStore {

    WorkflowInstance start(StartCommand command);

    Optional<ClaimedActivation> claim(
            String activationId,
            String workerGroup,
            int slotNo,
            String executorId,
            Instant leaseUntil,
            Instant now
    );

    void complete(CompleteCommand command);

    void retry(RetryCommand command);

    void fail(FailCommand command);

    List<Long> recoverExpiredLeases(Instant now, int limit);

    List<OutboxEvent> findUnpublishedOutbox(int limit);

    void markOutboxPublished(long outboxId);

    record ActivationSpec(
            String activationId,
            String nodeId,
            int activationNo,
            String workerGroup,
            int slotNo,
            int maxAttempts,
            int retryIntervalSeconds,
            int timeoutSeconds,
            String inputJson,
            Instant availableAt
    ) {
    }

    record StartCommand(
            String instanceId,
            String workflowId,
            int workflowVersion,
            long triggerExecutionId,
            String inputJson,
            Instant startedAt,
            ActivationSpec firstActivation,
            boolean completesImmediately
    ) {
    }

    record CompleteCommand(
            String activationId,
            int attempt,
            String routeHandle,
            String outputJson,
            Instant finishedAt,
            String edgeId,
            String targetNodeId,
            ActivationSpec nextActivation,
            boolean completesInstance
    ) {
    }

    record RetryCommand(
            String activationId,
            int attempt,
            String errorCode,
            String errorMessage,
            Instant failedAt,
            Instant availableAt,
            String workerGroup,
            int slotNo
    ) {
    }

    record FailCommand(
            String activationId,
            int attempt,
            String errorCode,
            String errorMessage,
            Instant failedAt
    ) {
    }
}
