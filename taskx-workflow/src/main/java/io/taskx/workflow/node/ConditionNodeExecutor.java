package io.taskx.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Deterministic schema-v1 condition. Config example:
 * {@code {"pointer":"/score","operator":"GTE","value":80,"trueHandle":"pass","falseHandle":"fail"}}.
 */
public final class ConditionNodeExecutor implements WorkflowNodeExecutor {

    private static final Set<String> OPERATORS =
            Set.of("EXISTS", "EQ", "NE", "GT", "GTE", "LT", "LTE");

    @Override
    public String type() {
        return "CONDITION";
    }

    @Override
    public int configVersion() {
        return 1;
    }

    @Override
    public void validate(JsonNode config) {
        Config parsed = parse(config);
        if (!"EXISTS".equals(parsed.operator()) && parsed.expected() == null) {
            throw new IllegalArgumentException("CONDITION config.value is required for " + parsed.operator());
        }
    }

    @Override
    public NodeResult execute(NodeContext context, JsonNode config) {
        Config parsed = parse(config);
        JsonNode actual = context.nodeInput() == null
                ? null
                : context.nodeInput().at(parsed.pointer());
        boolean matched = evaluate(actual, parsed.operator(), parsed.expected());
        return new NodeResult.Success(
                context.nodeInput(),
                matched ? parsed.trueHandle() : parsed.falseHandle()
        );
    }

    private static boolean evaluate(JsonNode actual, String operator, JsonNode expected) {
        boolean missing = actual == null || actual.isMissingNode() || actual.isNull();
        if ("EXISTS".equals(operator)) {
            return !missing;
        }
        if ("EQ".equals(operator)) {
            return !missing && actual.equals(expected);
        }
        if ("NE".equals(operator)) {
            return missing || !actual.equals(expected);
        }
        if (missing || expected == null) {
            return false;
        }
        int comparison;
        if (actual.isNumber() && expected.isNumber()) {
            comparison = new BigDecimal(actual.asText()).compareTo(new BigDecimal(expected.asText()));
        } else if (actual.isTextual() && expected.isTextual()) {
            comparison = actual.asText().compareTo(expected.asText());
        } else {
            return false;
        }
        return switch (operator) {
            case "GT" -> comparison > 0;
            case "GTE" -> comparison >= 0;
            case "LT" -> comparison < 0;
            case "LTE" -> comparison <= 0;
            default -> false;
        };
    }

    private static Config parse(JsonNode config) {
        if (config == null || !config.isObject()) {
            throw new IllegalArgumentException("CONDITION config must be an object");
        }
        String pointer = text(config, "pointer", null);
        if (pointer == null || (!pointer.isEmpty() && !pointer.startsWith("/"))) {
            throw new IllegalArgumentException("CONDITION config.pointer must be a JSON Pointer");
        }
        String operator = text(config, "operator", null);
        if (operator == null || !OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("unsupported CONDITION operator: " + operator);
        }
        return new Config(
                pointer,
                operator,
                config.get("value"),
                text(config, "trueHandle", "true"),
                text(config, "falseHandle", "false")
        );
    }

    private static String text(JsonNode config, String field, String defaultValue) {
        JsonNode value = config.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("CONDITION config." + field + " must be text");
        }
        return value.asText();
    }

    private record Config(
            String pointer,
            String operator,
            JsonNode expected,
            String trueHandle,
            String falseHandle
    ) {
    }
}
