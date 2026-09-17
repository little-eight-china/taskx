package io.taskx.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;

public sealed interface NodeResult {

    record Success(JsonNode output, String routeHandle) implements NodeResult {
        public Success {
            routeHandle = routeHandle == null || routeHandle.isBlank() ? "default" : routeHandle;
        }
    }

    record Failure(String errorCode, String message, boolean retryable) implements NodeResult {
        public Failure {
            errorCode = Objects.requireNonNullElse(errorCode, "NODE_FAILED");
            message = Objects.requireNonNullElse(message, "node failed");
        }
    }

    record Waiting(String callbackToken, Instant expiresAt) implements NodeResult {
        public Waiting {
            callbackToken = Objects.requireNonNull(callbackToken, "callbackToken");
            expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    static Success success(JsonNode output) {
        return new Success(output, "default");
    }
}
