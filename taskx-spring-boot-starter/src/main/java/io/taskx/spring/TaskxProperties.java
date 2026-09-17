package io.taskx.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = "taskx")
public record TaskxProperties(
        @DefaultValue("32") int slotCount,
        Mysql mysql,
        Redis redis,
        Executor executor
) {

    public static final String DEFAULT_MYSQL_URL =
            "jdbc:mysql://127.0.0.1:3306/taskx?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";

    public TaskxProperties {
        if (slotCount <= 0) {
            throw new IllegalArgumentException("taskx.slot-count must be > 0");
        }
        mysql = mysql == null ? new Mysql(DEFAULT_MYSQL_URL, "root", "taskx") : mysql;
        redis = redis == null ? new Redis("redis://127.0.0.1:6379") : redis;
        executor = Objects.requireNonNull(executor, "taskx.executor");
    }

    public record Mysql(
            @DefaultValue("jdbc:mysql://127.0.0.1:3306/taskx?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8")
            String url,
            @DefaultValue("root") String user,
            @DefaultValue("taskx") String password
    ) {
        public Mysql {
            url = Objects.requireNonNull(url, "taskx.mysql.url");
            user = Objects.requireNonNull(user, "taskx.mysql.user");
            password = password == null ? "" : password;
        }
    }

    public record Redis(@DefaultValue("redis://127.0.0.1:6379") String address) {
        public Redis {
            address = Objects.requireNonNull(address, "taskx.redis.address");
        }
    }

    public record Executor(
            String id,
            @DefaultValue("0") List<Integer> slots,
            @DefaultValue("default") List<String> workerGroups,
            @DefaultValue("true") boolean autoStart,
            @DefaultValue("4") int workerThreads,
            @DefaultValue("256") int queueCapacity
    ) {
        public Executor {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("taskx.executor.id is required");
            }
            slots = slots == null ? List.of(0) : List.copyOf(slots);
            if (slots.isEmpty()) {
                throw new IllegalArgumentException("taskx.executor.slots must not be empty");
            }
            workerGroups = workerGroups == null ? List.of("default") : List.copyOf(workerGroups);
            if (workerGroups.isEmpty() || workerGroups.stream().anyMatch(group -> group == null || group.isBlank())) {
                throw new IllegalArgumentException("taskx.executor.worker-groups must not be empty");
            }
            if (workerThreads <= 0) {
                throw new IllegalArgumentException("taskx.executor.worker-threads must be > 0");
            }
            if (queueCapacity <= 0) {
                throw new IllegalArgumentException("taskx.executor.queue-capacity must be > 0");
            }
        }
    }
}
