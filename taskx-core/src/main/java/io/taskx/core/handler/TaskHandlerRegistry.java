package io.taskx.core.handler;

import java.util.Optional;

public interface TaskHandlerRegistry {

    Optional<TaskHandler> find(String handlerName);
}
