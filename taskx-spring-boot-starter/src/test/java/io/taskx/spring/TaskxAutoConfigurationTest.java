package io.taskx.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TaskxAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskxAutoConfiguration.class));

    @Test
    void staysOffWhenExecutorIdMissing() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(TaskxExecutorEngine.class);
            assertThat(context).doesNotHaveBean(SpringTaskHandlerRegistry.class);
        });
    }
}
