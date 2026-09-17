package io.taskx.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Minimal HTTP schema v1. Secrets must be supplied by an external resolver in a later schema version;
 * do not store credentials directly in workflow JSON.
 */
public final class HttpNodeExecutor implements WorkflowNodeExecutor {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");

    private final HttpClient client;
    private final ObjectMapper mapper;

    public HttpNodeExecutor(HttpClient client, ObjectMapper mapper) {
        this.client = Objects.requireNonNull(client, "client");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public String type() {
        return "HTTP";
    }

    @Override
    public int configVersion() {
        return 1;
    }

    @Override
    public void validate(JsonNode config) {
        parse(config);
    }

    @Override
    public NodeResult execute(NodeContext context, JsonNode config) throws Exception {
        Config parsed = parse(config);
        HttpRequest.Builder request = HttpRequest.newBuilder(parsed.uri())
                .header("Idempotency-Key", context.idempotencyKey());
        if (context.timeoutSeconds() > 0) {
            request.timeout(Duration.ofSeconds(context.timeoutSeconds()));
        }
        parsed.headers().forEach(request::header);

        String body = parsed.body();
        if (body == null && context.nodeInput() != null && !context.nodeInput().isNull()) {
            body = mapper.writeValueAsString(context.nodeInput());
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        request.method(parsed.method(), publisher);

        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        ObjectNode output = mapper.createObjectNode();
        output.put("status", response.statusCode());
        ObjectNode headers = output.putObject("headers");
        response.headers().map().forEach((name, values) -> headers.putPOJO(name, values));
        String responseBody = response.body();
        if (responseBody == null || responseBody.isBlank()) {
            output.putNull("body");
        } else {
            try {
                output.set("body", mapper.readTree(responseBody));
            } catch (Exception ignored) {
                output.put("body", responseBody);
            }
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return NodeResult.success(output);
        }
        return new NodeResult.Failure(
                "HTTP_" + response.statusCode(),
                "HTTP node returned status " + response.statusCode(),
                response.statusCode() == 429 || response.statusCode() >= 500
        );
    }

    private static Config parse(JsonNode config) {
        if (config == null || !config.isObject()) {
            throw new IllegalArgumentException("HTTP config must be an object");
        }
        String rawUrl = requiredText(config, "url");
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("HTTP config.url is invalid", ex);
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("HTTP config.url must use http or https");
        }
        String method = config.hasNonNull("method")
                ? requiredText(config, "method").toUpperCase(Locale.ROOT)
                : "GET";
        if (!METHODS.contains(method)) {
            throw new IllegalArgumentException("unsupported HTTP method: " + method);
        }
        JsonNode headersNode = config.get("headers");
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        if (headersNode != null && !headersNode.isNull()) {
            if (!headersNode.isObject()) {
                throw new IllegalArgumentException("HTTP config.headers must be an object");
            }
            Iterator<Map.Entry<String, JsonNode>> fields = headersNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!field.getValue().isTextual()) {
                    throw new IllegalArgumentException("HTTP header values must be text");
                }
                headers.put(field.getKey(), field.getValue().asText());
            }
        }
        JsonNode bodyNode = config.get("body");
        String body = bodyNode == null || bodyNode.isNull()
                ? null
                : bodyNode.isTextual() ? bodyNode.asText() : bodyNode.toString();
        return new Config(uri, method, Map.copyOf(headers), body);
    }

    private static String requiredText(JsonNode config, String field) {
        JsonNode value = config.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("HTTP config." + field + " must not be blank");
        }
        return value.asText();
    }

    private record Config(URI uri, String method, Map<String, String> headers, String body) {
    }
}
