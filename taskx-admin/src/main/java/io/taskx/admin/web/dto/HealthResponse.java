package io.taskx.admin.web.dto;

public record HealthResponse(String status, String mysql, String redis) {
}
