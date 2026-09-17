package io.taskx.workflow.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskx.workflow.node.ConditionNodeExecutor;
import io.taskx.workflow.node.WorkflowNodeExecutorRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowDefinitionValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator(
            new WorkflowNodeExecutorRegistry(List.of(new ConditionNodeExecutor()))
    );

    @Test
    void acceptsVersionOneConditionDag() throws Exception {
        WorkflowDefinition definition = definition(List.of(
                edge("e1", "start", "default", "condition"),
                edge("e2", "condition", "true", "accepted"),
                edge("e3", "condition", "false", "rejected")
        ));

        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void rejectsCycles() throws Exception {
        WorkflowDefinition definition = definition(List.of(
                edge("e1", "start", "default", "condition"),
                edge("e2", "condition", "true", "accepted"),
                edge("e3", "condition", "false", "rejected"),
                edge("e4", "accepted", "default", "condition")
        ));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(definition));
    }

    private WorkflowDefinition definition(List<WorkflowDefinition.Edge> edges) throws Exception {
        return new WorkflowDefinition(
                1,
                List.of(
                        node("start", "START", null),
                        node(
                                "condition",
                                "CONDITION",
                                mapper.readTree("""
                                        {
                                          "pointer": "/score",
                                          "operator": "GTE",
                                          "value": 80,
                                          "trueHandle": "true",
                                          "falseHandle": "false"
                                        }
                                        """)
                        ),
                        node("accepted", "END", null),
                        node("rejected", "END", null)
                ),
                edges
        );
    }

    private static WorkflowDefinition.Node node(String id, String type, com.fasterxml.jackson.databind.JsonNode config) {
        return new WorkflowDefinition.Node(
                id,
                id,
                type,
                1,
                "default",
                30,
                WorkflowDefinition.RetryPolicy.NONE,
                config,
                null
        );
    }

    private static WorkflowDefinition.Edge edge(String id, String source, String handle, String target) {
        return new WorkflowDefinition.Edge(id, source, handle, target);
    }
}
