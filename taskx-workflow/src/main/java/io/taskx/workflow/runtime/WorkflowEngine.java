package io.taskx.workflow.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskx.core.slot.SlotConfig;
import io.taskx.workflow.definition.WorkflowDefinition;
import io.taskx.workflow.definition.WorkflowDefinitionCodec;
import io.taskx.workflow.definition.WorkflowDefinitionValidator;
import io.taskx.workflow.definition.WorkflowVersion;
import io.taskx.workflow.node.NodeContext;
import io.taskx.workflow.node.NodeResult;
import io.taskx.workflow.node.WorkflowNodeExecutor;
import io.taskx.workflow.node.WorkflowNodeExecutorRegistry;
import io.taskx.workflow.store.WorkflowDefinitionStore;
import io.taskx.workflow.store.WorkflowReadyIndex;
import io.taskx.workflow.store.WorkflowRuntimeStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Version-1 DAG coordinator. One route is selected per node; joins, loops and callbacks remain disabled.
 */
public final class WorkflowEngine {

    private final WorkflowDefinitionStore definitions;
    private final WorkflowRuntimeStore runtime;
    private final WorkflowReadyIndex readyIndex;
    private final WorkflowDefinitionCodec codec;
    private final WorkflowNodeExecutorRegistry executors;
    private final ObjectMapper mapper;
    private final SlotConfig slots;
    private final Clock clock;
    private final Duration leaseDuration;
    private final WorkflowCompletionListener completionListener;
    private final Executor workers;
    private final Supplier<String> ids;

    public WorkflowEngine(
            WorkflowDefinitionStore definitions,
            WorkflowRuntimeStore runtime,
            WorkflowReadyIndex readyIndex,
            WorkflowDefinitionCodec codec,
            WorkflowNodeExecutorRegistry executors,
            ObjectMapper mapper,
            SlotConfig slots,
            Clock clock,
            Duration leaseDuration
    ) {
        this(
                definitions,
                runtime,
                readyIndex,
                codec,
                executors,
                mapper,
                slots,
                clock,
                leaseDuration,
                WorkflowCompletionListener.NOOP,
                Runnable::run,
                () -> java.util.UUID.randomUUID().toString()
        );
    }

    public WorkflowEngine(
            WorkflowDefinitionStore definitions,
            WorkflowRuntimeStore runtime,
            WorkflowReadyIndex readyIndex,
            WorkflowDefinitionCodec codec,
            WorkflowNodeExecutorRegistry executors,
            ObjectMapper mapper,
            SlotConfig slots,
            Clock clock,
            Duration leaseDuration,
            WorkflowCompletionListener completionListener
    ) {
        this(
                definitions,
                runtime,
                readyIndex,
                codec,
                executors,
                mapper,
                slots,
                clock,
                leaseDuration,
                completionListener,
                Runnable::run,
                () -> java.util.UUID.randomUUID().toString()
        );
    }

    public WorkflowEngine(
            WorkflowDefinitionStore definitions,
            WorkflowRuntimeStore runtime,
            WorkflowReadyIndex readyIndex,
            WorkflowDefinitionCodec codec,
            WorkflowNodeExecutorRegistry executors,
            ObjectMapper mapper,
            SlotConfig slots,
            Clock clock,
            Duration leaseDuration,
            WorkflowCompletionListener completionListener,
            Executor workers
    ) {
        this(
                definitions,
                runtime,
                readyIndex,
                codec,
                executors,
                mapper,
                slots,
                clock,
                leaseDuration,
                completionListener,
                workers,
                () -> java.util.UUID.randomUUID().toString()
        );
    }

    WorkflowEngine(
            WorkflowDefinitionStore definitions,
            WorkflowRuntimeStore runtime,
            WorkflowReadyIndex readyIndex,
            WorkflowDefinitionCodec codec,
            WorkflowNodeExecutorRegistry executors,
            ObjectMapper mapper,
            SlotConfig slots,
            Clock clock,
            Duration leaseDuration,
            WorkflowCompletionListener completionListener,
            Executor workers,
            Supplier<String> ids
    ) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.readyIndex = Objects.requireNonNull(readyIndex, "readyIndex");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.slots = Objects.requireNonNull(slots, "slots");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        this.completionListener = Objects.requireNonNull(completionListener, "completionListener");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.ids = Objects.requireNonNull(ids, "ids");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
    }

    public WorkflowInstance start(
            String workflowId,
            Integer pinnedVersion,
            long triggerExecutionId,
            String inputJson
    ) {
        WorkflowVersion version = pinnedVersion == null
                ? definitions.findCurrentPublished(workflowId)
                        .orElseThrow(() -> new NoSuchElementException("published workflow not found: " + workflowId))
                : definitions.findVersion(workflowId, pinnedVersion)
                        .orElseThrow(() -> new NoSuchElementException(
                                "workflow version not found: " + workflowId + '/' + pinnedVersion));
        if (version.status() != WorkflowVersion.Status.PUBLISHED) {
            throw new IllegalStateException("workflow version is not published: " + workflowId + '/' + version.version());
        }

        WorkflowDefinition graph = codec.decode(version.definitionJson());
        WorkflowDefinition.Node start = graph.nodes().stream()
                .filter(node -> WorkflowDefinitionValidator.START.equals(node.type()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("published workflow has no START"));
        WorkflowDefinition.Edge firstEdge = edgeFor(graph, start.id(), "default")
                .orElseThrow(() -> new IllegalStateException("START must have one default edge"));
        WorkflowDefinition.Node firstNode = node(graph, firstEdge.targetNodeId());
        Instant now = clock.instant();
        String instanceId = ids.get();
        boolean completesImmediately = WorkflowDefinitionValidator.END.equals(firstNode.type());
        WorkflowRuntimeStore.ActivationSpec activation = completesImmediately
                ? null
                : activation(firstNode, inputJson, now);
        WorkflowInstance instance = runtime.start(new WorkflowRuntimeStore.StartCommand(
                instanceId,
                workflowId,
                version.version(),
                triggerExecutionId,
                inputJson,
                now,
                activation,
                completesImmediately
        ));
        if (completesImmediately) {
            completionListener.onTerminal(triggerExecutionId, WorkflowInstanceStatus.SUCCESS);
        }
        return instance;
    }

    public int tick(String workerGroup, int slotNo, String executorId, int limit) {
        Instant now = clock.instant();
        List<String> due = readyIndex.rangeDue(workerGroup, slotNo, now, limit);
        int claimed = 0;
        for (String activationId : due) {
            Optional<ClaimedActivation> row = runtime.claim(
                    activationId,
                    workerGroup,
                    slotNo,
                    executorId,
                    now.plus(leaseDuration),
                    now
            );
            // Missing/non-ready members are stale Redis projections and are safe to remove.
            readyIndex.remove(workerGroup, slotNo, activationId);
            if (row.isEmpty()) {
                continue;
            }
            claimed++;
            ClaimedActivation claimedRow = row.get();
            try {
                workers.execute(() -> execute(claimedRow, workerGroup, slotNo));
            } catch (RejectedExecutionException ignored) {
                // The MySQL lease recovery path will requeue this activation.
            }
        }
        return claimed;
    }

    public int recoverExpiredLeases(int limit) {
        List<Long> failedRoots = runtime.recoverExpiredLeases(clock.instant(), limit);
        failedRoots.forEach(rootExecutionId ->
                completionListener.onTerminal(rootExecutionId, WorkflowInstanceStatus.FAILED));
        return failedRoots.size();
    }

    private void execute(ClaimedActivation row, String workerGroup, int slotNo) {
        WorkflowVersion version = definitions.findVersion(row.workflowId(), row.workflowVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "workflow version disappeared: " + row.workflowId() + '/' + row.workflowVersion()));
        WorkflowDefinition graph = codec.decode(version.definitionJson());
        WorkflowDefinition.Node node = node(graph, row.nodeId());
        WorkflowNodeExecutor executor = executors.find(node.type(), node.configVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "workflow node executor disappeared: " + node.type() + '/' + node.configVersion()));

        NodeResult result;
        try {
            result = executor.execute(
                    new NodeContext(
                            row.instanceId(),
                            row.activationId(),
                            row.attempt(),
                            row.idempotencyKey(),
                            node.timeoutSeconds(),
                            json(row.workflowInputJson()),
                            json(row.inputJson())
                    ),
                    node.config()
            );
        } catch (Exception ex) {
            result = new NodeResult.Failure("NODE_EXCEPTION", safeMessage(ex), true);
        }
        applyResult(row, graph, node, result, workerGroup, slotNo);
    }

    private void applyResult(
            ClaimedActivation row,
            WorkflowDefinition graph,
            WorkflowDefinition.Node node,
            NodeResult result,
            String workerGroup,
            int slotNo
    ) {
        Instant now = clock.instant();
        if (result instanceof NodeResult.Success success) {
            WorkflowDefinition.Edge edge = edgeFor(graph, node.id(), success.routeHandle())
                    .orElse(null);
            if (edge == null) {
                fail(row, "ROUTE_NOT_FOUND", "no edge for route handle " + success.routeHandle(), now);
                return;
            }
            WorkflowDefinition.Node target = node(graph, edge.targetNodeId());
            boolean completes = WorkflowDefinitionValidator.END.equals(target.type());
            String outputJson = stringify(success.output());
            WorkflowRuntimeStore.ActivationSpec next = completes
                    ? null
                    : activation(target, outputJson, now);
            runtime.complete(new WorkflowRuntimeStore.CompleteCommand(
                    row.activationId(),
                    row.attempt(),
                    success.routeHandle(),
                    outputJson,
                    now,
                    edge.id(),
                    target.id(),
                    next,
                    completes
            ));
            if (completes) {
                completionListener.onTerminal(row.triggerExecutionId(), WorkflowInstanceStatus.SUCCESS);
            }
            return;
        }

        if (result instanceof NodeResult.Failure failure) {
            boolean retry = failure.retryable() && row.attempt() < node.retryPolicy().maxAttempts();
            if (retry) {
                Instant availableAt = now.plusSeconds(node.retryPolicy().intervalSeconds());
                runtime.retry(new WorkflowRuntimeStore.RetryCommand(
                        row.activationId(),
                        row.attempt(),
                        failure.errorCode(),
                        failure.message(),
                        now,
                        availableAt,
                        workerGroup,
                        slotNo
                ));
            } else {
                fail(row, failure.errorCode(), failure.message(), now);
            }
            return;
        }

        fail(
                row,
                "UNSUPPORTED_RESULT",
                "WAITING callbacks are reserved but not enabled in workflow schema version 1",
                now
        );
    }

    private void fail(ClaimedActivation row, String errorCode, String message, Instant failedAt) {
        runtime.fail(new WorkflowRuntimeStore.FailCommand(
                row.activationId(),
                row.attempt(),
                errorCode,
                message,
                failedAt
        ));
        completionListener.onTerminal(row.triggerExecutionId(), WorkflowInstanceStatus.FAILED);
    }

    private WorkflowRuntimeStore.ActivationSpec activation(
            WorkflowDefinition.Node node,
            String inputJson,
            Instant availableAt
    ) {
        String activationId = ids.get();
        return new WorkflowRuntimeStore.ActivationSpec(
                activationId,
                node.id(),
                1,
                node.workerGroup(),
                slots.slotOf(activationId),
                node.retryPolicy().maxAttempts(),
                node.retryPolicy().intervalSeconds(),
                node.timeoutSeconds(),
                inputJson,
                availableAt
        );
    }

    private static WorkflowDefinition.Node node(WorkflowDefinition graph, String nodeId) {
        return graph.nodes().stream()
                .filter(node -> node.id().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("workflow node not found: " + nodeId));
    }

    private static Optional<WorkflowDefinition.Edge> edgeFor(
            WorkflowDefinition graph,
            String sourceNodeId,
            String routeHandle
    ) {
        return graph.edges().stream()
                .filter(edge -> edge.sourceNodeId().equals(sourceNodeId))
                .filter(edge -> edge.sourceHandle().equals(routeHandle))
                .findFirst();
    }

    private JsonNode json(String value) {
        if (value == null || value.isBlank()) {
            return mapper.nullNode();
        }
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("runtime JSON is invalid", ex);
        }
    }

    private String stringify(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("node output is not serializable", ex);
        }
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
