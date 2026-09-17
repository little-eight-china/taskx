package io.taskx.workflow.definition;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;

/**
 * Immutable semantic graph stored as one JSON document per published version.
 * UI coordinates are stored separately and do not affect execution semantics.
 */
public record WorkflowDefinition(
        int schemaVersion,
        List<Node> nodes,
        List<Edge> edges
) {

    public WorkflowDefinition {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be > 0");
        }
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
    }

    public record Node(
            String id,
            String name,
            String type,
            int configVersion,
            String workerGroup,
            int timeoutSeconds,
            RetryPolicy retryPolicy,
            JsonNode config,
            JsonNode inputMapping
    ) {

        public Node {
            id = requireText(id, "node.id");
            name = requireText(name, "node.name");
            type = requireText(type, "node.type").toUpperCase();
            if (configVersion <= 0) {
                throw new IllegalArgumentException("node.configVersion must be > 0");
            }
            workerGroup = workerGroup == null || workerGroup.isBlank() ? "default" : workerGroup;
            if (timeoutSeconds < 0) {
                throw new IllegalArgumentException("node.timeoutSeconds must be >= 0");
            }
            retryPolicy = retryPolicy == null ? RetryPolicy.NONE : retryPolicy;
        }
    }

    public record Edge(
            String id,
            String sourceNodeId,
            String sourceHandle,
            String targetNodeId
    ) {

        public Edge {
            id = requireText(id, "edge.id");
            sourceNodeId = requireText(sourceNodeId, "edge.sourceNodeId");
            sourceHandle = sourceHandle == null || sourceHandle.isBlank() ? "default" : sourceHandle;
            targetNodeId = requireText(targetNodeId, "edge.targetNodeId");
        }
    }

    public record RetryPolicy(int maxAttempts, int intervalSeconds) {

        public static final RetryPolicy NONE = new RetryPolicy(1, 0);

        public RetryPolicy {
            if (maxAttempts <= 0) {
                throw new IllegalArgumentException("retryPolicy.maxAttempts must be > 0");
            }
            if (intervalSeconds < 0) {
                throw new IllegalArgumentException("retryPolicy.intervalSeconds must be >= 0");
            }
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
