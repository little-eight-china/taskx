package io.taskx.spring;

import io.taskx.core.handler.MapTaskHandlerRegistry;
import io.taskx.core.handler.TaskHandler;
import io.taskx.core.handler.TaskHandlerRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Collects {@link TaskHandler} beans. {@link TaskxHandler} wins over the Spring bean name.
 */
public final class SpringTaskHandlerRegistry implements TaskHandlerRegistry {

    private final MapTaskHandlerRegistry delegate = new MapTaskHandlerRegistry();
    private final Set<String> names;

    private SpringTaskHandlerRegistry(Map<String, TaskHandler> handlers) {
        handlers.forEach(delegate::put);
        this.names = Set.copyOf(handlers.keySet());
    }

    public static SpringTaskHandlerRegistry from(ConfigurableListableBeanFactory beanFactory) {
        Objects.requireNonNull(beanFactory, "beanFactory");
        Map<String, TaskHandler> byHandlerName = new LinkedHashMap<>();
        beanFactory.getBeansOfType(TaskHandler.class).forEach((beanName, handler) -> {
            String handlerName = resolveName(beanFactory, beanName);
            TaskHandler existing = byHandlerName.putIfAbsent(handlerName, handler);
            if (existing != null) {
                throw new IllegalStateException("duplicate TaskHandler name '" + handlerName + "'");
            }
        });
        return new SpringTaskHandlerRegistry(byHandlerName);
    }

    public Set<String> names() {
        return names;
    }

    @Override
    public Optional<TaskHandler> find(String handlerName) {
        return delegate.find(handlerName);
    }

    private static String resolveName(ConfigurableListableBeanFactory beanFactory, String beanName) {
        TaskxHandler annotation = beanFactory.findAnnotationOnBean(beanName, TaskxHandler.class);
        if (annotation == null) {
            return beanName;
        }
        String value = annotation.value();
        if (value.isBlank()) {
            throw new IllegalStateException("@TaskxHandler on bean '" + beanName + "' must not be blank");
        }
        return value;
    }
}
