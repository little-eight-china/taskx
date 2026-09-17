package io.taskx.spring;

import io.taskx.workflow.node.WorkflowHandler;
import io.taskx.workflow.node.WorkflowHandlerRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class SpringWorkflowHandlers {

    private SpringWorkflowHandlers() {
    }

    static WorkflowHandlerRegistry from(ConfigurableListableBeanFactory beanFactory) {
        Objects.requireNonNull(beanFactory, "beanFactory");
        Map<String, WorkflowHandler> handlers = new LinkedHashMap<>();
        beanFactory.getBeansOfType(WorkflowHandler.class).forEach((beanName, handler) -> {
            TaskxHandler annotation = beanFactory.findAnnotationOnBean(beanName, TaskxHandler.class);
            String name = annotation == null ? beanName : annotation.value();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("@TaskxHandler on bean '" + beanName + "' must not be blank");
            }
            if (handlers.putIfAbsent(name, handler) != null) {
                throw new IllegalStateException("duplicate WorkflowHandler name '" + name + "'");
            }
        });
        return new WorkflowHandlerRegistry(handlers);
    }
}
