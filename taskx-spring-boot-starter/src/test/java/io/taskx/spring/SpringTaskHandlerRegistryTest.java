package io.taskx.spring;

import io.taskx.core.domain.Execution;
import io.taskx.core.domain.Task;
import io.taskx.core.handler.TaskHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringTaskHandlerRegistryTest {

    @Test
    void annotationOverridesBeanNameAndBeanNameIsFallback() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Handlers.class)) {
            SpringTaskHandlerRegistry registry = SpringTaskHandlerRegistry.from(context.getBeanFactory());
            assertEquals(Set.of("demo", "other"), registry.names());
            assertTrue(registry.find("demo").isPresent());
            assertTrue(registry.find("other").isPresent());
        }
    }

    @Test
    void typeLevelAnnotationOnComponent() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TypedConfig.class)) {
            SpringTaskHandlerRegistry registry = SpringTaskHandlerRegistry.from(context.getBeanFactory());
            assertEquals(Set.of("typed"), registry.names());
        }
    }

    @Test
    void duplicateNamesFail() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Duplicates.class)) {
            assertThrows(IllegalStateException.class, () -> SpringTaskHandlerRegistry.from(context.getBeanFactory()));
        }
    }

    @Test
    void emptyHandlersAreAllowedForWorkflowOnlyApplications() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            SpringTaskHandlerRegistry registry = SpringTaskHandlerRegistry.from(context.getBeanFactory());
            assertTrue(registry.names().isEmpty());
        }
    }

    @Configuration
    static class Handlers {
        @Bean("ignored")
        @TaskxHandler("demo")
        TaskHandler demo() {
            return SpringTaskHandlerRegistryTest::noop;
        }

        @Bean("other")
        TaskHandler other() {
            return SpringTaskHandlerRegistryTest::noop;
        }
    }

    @Configuration
    static class Duplicates {
        @Bean
        @TaskxHandler("same")
        TaskHandler first() {
            return SpringTaskHandlerRegistryTest::noop;
        }

        @Bean
        @TaskxHandler("same")
        TaskHandler second() {
            return SpringTaskHandlerRegistryTest::noop;
        }
    }

    @Configuration
    static class TypedConfig {
        @Bean
        TypedHandler typedHandler() {
            return new TypedHandler();
        }
    }

    @TaskxHandler("typed")
    static class TypedHandler implements TaskHandler {
        @Override
        public void handle(Task task, Execution execution) {
        }
    }

    private static void noop(Task task, Execution execution) {
    }
}
