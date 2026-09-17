package io.taskx.workflow.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

public final class WorkflowDefinitionCodec {

    private final ObjectMapper mapper;

    public WorkflowDefinitionCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public WorkflowDefinition decode(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("definition json must not be blank");
        }
        try {
            return mapper.readValue(json, WorkflowDefinition.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid workflow definition json", ex);
        }
    }

    public String encode(WorkflowDefinition definition) {
        try {
            return mapper.writeValueAsString(Objects.requireNonNull(definition, "definition"));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("could not encode workflow definition", ex);
        }
    }
}
