package io.taskx.workflow.node;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WorkflowNodeExecutorRegistry {

    private final Map<Key, WorkflowNodeExecutor> executors;

    public WorkflowNodeExecutorRegistry(Collection<? extends WorkflowNodeExecutor> executors) {
        Map<Key, WorkflowNodeExecutor> byKey = new LinkedHashMap<>();
        for (WorkflowNodeExecutor executor : Objects.requireNonNull(executors, "executors")) {
            Key key = new Key(executor.type(), executor.configVersion());
            if (byKey.putIfAbsent(key, executor) != null) {
                throw new IllegalArgumentException("duplicate workflow node executor " + key);
            }
        }
        this.executors = Map.copyOf(byKey);
    }

    public Optional<WorkflowNodeExecutor> find(String type, int configVersion) {
        return Optional.ofNullable(executors.get(new Key(type, configVersion)));
    }

    private record Key(String type, int configVersion) {
        private Key {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("node executor type must not be blank");
            }
            type = type.toUpperCase(Locale.ROOT);
            if (configVersion <= 0) {
                throw new IllegalArgumentException("configVersion must be > 0");
            }
        }
    }
}
