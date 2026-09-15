package io.taskx.core.handler;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MapTaskHandlerRegistry implements TaskHandlerRegistry {

    private final ConcurrentHashMap<String, TaskHandler> handlers = new ConcurrentHashMap<>();

    public MapTaskHandlerRegistry put(String name, TaskHandler handler) {
        handlers.put(Objects.requireNonNull(name), Objects.requireNonNull(handler));
        return this;
    }

    @Override
    public Optional<TaskHandler> find(String handlerName) {
        return Optional.ofNullable(handlers.get(handlerName));
    }
}
