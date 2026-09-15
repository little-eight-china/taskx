package io.taskx.executor;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record ExecutorSettings(
        String executorId,
        List<Integer> slots,
        int slotCount,
        String mysqlUrl,
        String mysqlUser,
        String mysqlPassword,
        String redisAddress,
        boolean seedDemoTask
) {

    public ExecutorSettings {
        Objects.requireNonNull(executorId);
        if (executorId.isBlank()) {
            throw new IllegalArgumentException("TASKX_EXECUTOR_ID is required");
        }
        slots = List.copyOf(slots);
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("TASKX_SLOTS is required");
        }
        if (slotCount <= 0) {
            throw new IllegalArgumentException("TASKX_SLOT_COUNT must be > 0");
        }
        mysqlUrl = Objects.requireNonNull(mysqlUrl);
        mysqlUser = Objects.requireNonNull(mysqlUser);
        mysqlPassword = mysqlPassword == null ? "" : mysqlPassword;
        redisAddress = Objects.requireNonNull(redisAddress);
    }

    public static ExecutorSettings fromEnv() {
        String executorId = env("TASKX_EXECUTOR_ID", "");
        List<Integer> slots = Arrays.stream(env("TASKX_SLOTS", "0").split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(Integer::valueOf)
                .toList();
        return new ExecutorSettings(
                executorId,
                slots,
                Integer.parseInt(env("TASKX_SLOT_COUNT", "32")),
                env("TASKX_MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/taskx?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8"),
                env("TASKX_MYSQL_USER", "root"),
                env("TASKX_MYSQL_PASSWORD", "taskx"),
                env("TASKX_REDIS_ADDRESS", "redis://127.0.0.1:6379"),
                Boolean.parseBoolean(env("TASKX_SEED_DEMO", "false"))
        );
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
