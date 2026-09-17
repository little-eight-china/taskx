package io.taskx.workflow.definition;

import io.taskx.workflow.node.WorkflowNodeExecutor;
import io.taskx.workflow.node.WorkflowNodeExecutorRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Publish-time validation. Version 1 deliberately accepts only acyclic outer graphs.
 */
public final class WorkflowDefinitionValidator {

    public static final String START = "START";
    public static final String END = "END";

    private final WorkflowNodeExecutorRegistry executors;

    public WorkflowDefinitionValidator(WorkflowNodeExecutorRegistry executors) {
        this.executors = Objects.requireNonNull(executors, "executors");
    }

    public void validate(WorkflowDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Map<String, WorkflowDefinition.Node> nodes = indexNodes(definition.nodes());
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("workflow must contain nodes");
        }
        List<WorkflowDefinition.Node> starts = nodes.values().stream()
                .filter(node -> START.equals(node.type()))
                .toList();
        if (starts.size() != 1) {
            throw new IllegalArgumentException("workflow must contain exactly one START node");
        }
        if (nodes.values().stream().noneMatch(node -> END.equals(node.type()))) {
            throw new IllegalArgumentException("workflow must contain at least one END node");
        }

        validateNodeConfigs(nodes.values());
        Graph graph = buildGraph(nodes, definition.edges());
        String startId = starts.getFirst().id();
        if (graph.inDegree().get(startId) != 0) {
            throw new IllegalArgumentException("START node must not have incoming edges");
        }
        List<WorkflowDefinition.Edge> startEdges = graph.outgoing().get(startId);
        if (startEdges.size() != 1 || !"default".equals(startEdges.getFirst().sourceHandle())) {
            throw new IllegalArgumentException("START node must have exactly one default edge");
        }
        for (WorkflowDefinition.Node node : nodes.values()) {
            if (END.equals(node.type()) && !graph.outgoing().get(node.id()).isEmpty()) {
                throw new IllegalArgumentException("END node must not have outgoing edges: " + node.id());
            }
            if (!END.equals(node.type()) && graph.outgoing().get(node.id()).isEmpty()) {
                throw new IllegalArgumentException("non-END node must have outgoing edges: " + node.id());
            }
        }
        ensureAcyclic(graph);
        ensureReachable(startId, nodes.keySet(), graph.outgoing());
    }

    private Map<String, WorkflowDefinition.Node> indexNodes(List<WorkflowDefinition.Node> source) {
        Map<String, WorkflowDefinition.Node> nodes = new LinkedHashMap<>();
        for (WorkflowDefinition.Node node : source) {
            if (nodes.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("duplicate node id: " + node.id());
            }
        }
        return nodes;
    }

    private void validateNodeConfigs(Iterable<WorkflowDefinition.Node> nodes) {
        for (WorkflowDefinition.Node node : nodes) {
            if (START.equals(node.type()) || END.equals(node.type())) {
                continue;
            }
            if (node.inputMapping() != null && !node.inputMapping().isNull()) {
                throw new IllegalArgumentException(
                        "inputMapping is reserved but not enabled in workflow schema version 1: " + node.id());
            }
            WorkflowNodeExecutor executor = executors.find(node.type(), node.configVersion())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unsupported node type/configVersion: " + node.type() + "/" + node.configVersion()));
            executor.validate(node.config());
        }
    }

    private Graph buildGraph(
            Map<String, WorkflowDefinition.Node> nodes,
            List<WorkflowDefinition.Edge> edges
    ) {
        Map<String, List<WorkflowDefinition.Edge>> outgoing = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        nodes.keySet().forEach(id -> {
            outgoing.put(id, new ArrayList<>());
            inDegree.put(id, 0);
        });
        Set<String> edgeIds = new HashSet<>();
        Set<String> handles = new HashSet<>();
        for (WorkflowDefinition.Edge edge : edges) {
            if (!edgeIds.add(edge.id())) {
                throw new IllegalArgumentException("duplicate edge id: " + edge.id());
            }
            if (!nodes.containsKey(edge.sourceNodeId()) || !nodes.containsKey(edge.targetNodeId())) {
                throw new IllegalArgumentException("edge references missing node: " + edge.id());
            }
            String handleKey = edge.sourceNodeId() + '\0' + edge.sourceHandle();
            if (!handles.add(handleKey)) {
                throw new IllegalArgumentException(
                        "duplicate outgoing sourceHandle '" + edge.sourceHandle() + "' on " + edge.sourceNodeId());
            }
            outgoing.get(edge.sourceNodeId()).add(edge);
            inDegree.compute(edge.targetNodeId(), (key, value) -> value + 1);
        }
        return new Graph(outgoing, inDegree);
    }

    private void ensureAcyclic(Graph graph) {
        Map<String, Integer> degrees = new HashMap<>(graph.inDegree());
        ArrayDeque<String> ready = new ArrayDeque<>();
        degrees.forEach((node, degree) -> {
            if (degree == 0) {
                ready.add(node);
            }
        });
        int visited = 0;
        while (!ready.isEmpty()) {
            String node = ready.removeFirst();
            visited++;
            for (WorkflowDefinition.Edge edge : graph.outgoing().get(node)) {
                int degree = degrees.compute(edge.targetNodeId(), (key, value) -> value - 1);
                if (degree == 0) {
                    ready.add(edge.targetNodeId());
                }
            }
        }
        if (visited != degrees.size()) {
            throw new IllegalArgumentException("workflow graph must be acyclic; use a controlled LOOP node later");
        }
    }

    private void ensureReachable(
            String startId,
            Set<String> allNodes,
            Map<String, List<WorkflowDefinition.Edge>> outgoing
    ) {
        Set<String> reached = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        while (!queue.isEmpty()) {
            String node = queue.removeFirst();
            if (!reached.add(node)) {
                continue;
            }
            outgoing.get(node).forEach(edge -> queue.add(edge.targetNodeId()));
        }
        if (!reached.equals(allNodes)) {
            Set<String> unreachable = new HashSet<>(allNodes);
            unreachable.removeAll(reached);
            throw new IllegalArgumentException("unreachable workflow nodes: " + unreachable);
        }
    }

    private record Graph(
            Map<String, List<WorkflowDefinition.Edge>> outgoing,
            Map<String, Integer> inDegree
    ) {
    }
}
