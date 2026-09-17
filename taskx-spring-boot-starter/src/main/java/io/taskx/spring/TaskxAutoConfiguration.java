package io.taskx.spring;

import io.taskx.core.handler.TaskHandlerRegistry;
import io.taskx.workflow.node.WorkflowHandlerRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(TaskxProperties.class)
@ConditionalOnProperty(prefix = "taskx.executor", name = "id")
public class TaskxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TaskHandlerRegistry.class)
    TaskHandlerRegistry taskHandlerRegistry(ConfigurableListableBeanFactory beanFactory) {
        return SpringTaskHandlerRegistry.from(beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowHandlerRegistry.class)
    WorkflowHandlerRegistry workflowHandlerRegistry(ConfigurableListableBeanFactory beanFactory) {
        return SpringWorkflowHandlers.from(beanFactory);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(TaskxExecutorEngine.class)
    TaskxExecutorEngine taskxExecutorEngine(
            TaskxProperties properties,
            TaskHandlerRegistry handlers,
            WorkflowHandlerRegistry workflowHandlers
    ) {
        return new TaskxExecutorEngine(properties, handlers, workflowHandlers);
    }
}
