package io.taskx.admin;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskx.common.lock.DistributedLock;
import io.taskx.common.lock.LockSettings;
import io.taskx.core.clock.EpochClock;
import io.taskx.core.config.TaskConfigService;
import io.taskx.core.maintenance.SlotReassignService;
import io.taskx.core.maintenance.TriggerRebuildService;
import io.taskx.core.schedule.NextFireCalculator;
import io.taskx.core.slot.SlotConfig;
import io.taskx.core.store.ExecutionRepository;
import io.taskx.core.store.SlotOwnershipRepository;
import io.taskx.core.store.TaskRepository;
import io.taskx.core.store.TriggerIndex;
import io.taskx.meta.config.JdbcRedisTaskConfigService;
import io.taskx.meta.jdbc.JdbcExecutionRepository;
import io.taskx.meta.jdbc.JdbcSlotOwnershipRepository;
import io.taskx.meta.jdbc.JdbcTaskRepository;
import io.taskx.meta.jdbc.JdbcWorkflowDefinitionStore;
import io.taskx.meta.redis.RedisEpochClock;
import io.taskx.meta.redis.RedissonClients;
import io.taskx.meta.redis.RedissonDistributedLock;
import io.taskx.meta.redis.RedissonTriggerIndex;
import io.taskx.workflow.definition.WorkflowDefinitionCodec;
import io.taskx.workflow.definition.WorkflowDefinitionValidator;
import io.taskx.workflow.definition.WorkflowPublishingService;
import io.taskx.workflow.node.ConditionNodeExecutor;
import io.taskx.workflow.node.HandlerNodeExecutor;
import io.taskx.workflow.node.HttpNodeExecutor;
import io.taskx.workflow.node.WorkflowHandlerRegistry;
import io.taskx.workflow.node.WorkflowNodeExecutorRegistry;
import io.taskx.workflow.store.WorkflowDefinitionStore;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.List;
import java.util.Map;

@Configuration
public class AdminConfiguration {

    @Bean(destroyMethod = "close")
    HikariDataSource dataSource(AdminProperties properties) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(properties.mysql().url());
        hikari.setUsername(properties.mysql().user());
        hikari.setPassword(properties.mysql().password());
        hikari.setMaximumPoolSize(8);
        hikari.setPoolName("taskx-admin");
        return new HikariDataSource(hikari);
    }

    @Bean(destroyMethod = "shutdown")
    RedissonClient redissonClient(AdminProperties properties) {
        return RedissonClients.singleServer(properties.redis().address(), LockSettings.DEFAULT);
    }

    @Bean
    SlotConfig slotConfig(AdminProperties properties) {
        return new SlotConfig(properties.slotCount());
    }

    @Bean
    DistributedLock distributedLock(RedissonClient redisson) {
        return new RedissonDistributedLock(redisson, LockSettings.DEFAULT);
    }

    @Bean
    TriggerIndex triggerIndex(RedissonClient redisson) {
        return new RedissonTriggerIndex(redisson);
    }

    @Bean
    EpochClock epochClock(RedissonClient redisson) {
        return new RedisEpochClock(redisson);
    }

    @Bean
    NextFireCalculator nextFireCalculator() {
        return new NextFireCalculator();
    }

    @Bean
    JdbcTaskRepository jdbcTaskRepository(DataSource dataSource) {
        return new JdbcTaskRepository(dataSource);
    }

    @Bean
    ExecutionRepository executionRepository(DataSource dataSource) {
        return new JdbcExecutionRepository(dataSource);
    }

    @Bean
    JdbcSlotOwnershipRepository jdbcSlotOwnershipRepository(DataSource dataSource) {
        return new JdbcSlotOwnershipRepository(dataSource);
    }

    @Bean
    WorkflowDefinitionStore workflowDefinitionStore(DataSource dataSource) {
        return new JdbcWorkflowDefinitionStore(dataSource);
    }

    @Bean
    WorkflowDefinitionCodec workflowDefinitionCodec(ObjectMapper mapper) {
        return new WorkflowDefinitionCodec(mapper);
    }

    @Bean
    WorkflowNodeExecutorRegistry workflowNodeExecutorRegistry(ObjectMapper mapper) {
        return new WorkflowNodeExecutorRegistry(List.of(
                new HandlerNodeExecutor(new WorkflowHandlerRegistry(Map.of())),
                new HttpNodeExecutor(HttpClient.newHttpClient(), mapper),
                new ConditionNodeExecutor()
        ));
    }

    @Bean
    WorkflowPublishingService workflowPublishingService(
            WorkflowDefinitionStore store,
            WorkflowDefinitionCodec codec,
            WorkflowNodeExecutorRegistry executors
    ) {
        return new WorkflowPublishingService(
                store,
                codec,
                new WorkflowDefinitionValidator(executors),
                Clock.systemUTC()
        );
    }

    @Bean
    TaskConfigService taskConfigService(
            DataSource dataSource,
            JdbcTaskRepository tasks,
            DistributedLock lock,
            TriggerIndex triggerIndex,
            EpochClock clock,
            NextFireCalculator calculator,
            SlotConfig slots
    ) {
        return new JdbcRedisTaskConfigService(dataSource, tasks, lock, triggerIndex, clock, calculator, slots);
    }

    @Bean
    TriggerRebuildService triggerRebuildService(
            DistributedLock lock,
            TriggerIndex triggerIndex,
            TaskRepository tasks,
            EpochClock clock,
            NextFireCalculator calculator,
            SlotConfig slots
    ) {
        return new TriggerRebuildService(lock, triggerIndex, tasks, clock, calculator, slots);
    }

    @Bean
    SlotReassignService slotReassignService(
            DistributedLock lock,
            SlotOwnershipRepository ownership,
            SlotConfig slots
    ) {
        return new SlotReassignService(lock, ownership, slots);
    }

    @Bean
    WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "PUT", "POST", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
