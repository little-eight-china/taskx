package io.taskx.spring;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskx.common.lock.LockSettings;
import io.taskx.core.handler.TaskHandlerRegistry;
import io.taskx.core.poll.Dispatch;
import io.taskx.core.poll.LockingTerminalScoreWriter;
import io.taskx.core.poll.RecoverLoop;
import io.taskx.core.poll.TriggerLoop;
import io.taskx.core.runtime.ExecutorRuntime;
import io.taskx.core.schedule.NextFireCalculator;
import io.taskx.core.slot.SlotConfig;
import io.taskx.meta.jdbc.JdbcExecutionRepository;
import io.taskx.meta.jdbc.JdbcSlotOwnershipRepository;
import io.taskx.meta.jdbc.JdbcTaskRepository;
import io.taskx.meta.jdbc.JdbcWorkflowDefinitionStore;
import io.taskx.meta.jdbc.JdbcWorkflowRuntimeStore;
import io.taskx.meta.redis.RedisEpochClock;
import io.taskx.meta.redis.RedissonClients;
import io.taskx.meta.redis.RedissonDistributedLock;
import io.taskx.meta.redis.RedissonTriggerIndex;
import io.taskx.meta.redis.RedissonWorkflowReadyIndex;
import io.taskx.workflow.definition.WorkflowDefinitionCodec;
import io.taskx.workflow.node.ConditionNodeExecutor;
import io.taskx.workflow.node.HandlerNodeExecutor;
import io.taskx.workflow.node.HttpNodeExecutor;
import io.taskx.workflow.node.WorkflowHandlerRegistry;
import io.taskx.workflow.node.WorkflowNodeExecutorRegistry;
import io.taskx.workflow.runtime.EngineWorkflowLauncher;
import io.taskx.workflow.runtime.FixedDelayWorkflowCompletionListener;
import io.taskx.workflow.runtime.WorkflowEngine;
import io.taskx.workflow.runtime.WorkflowOutboxPublisher;
import io.taskx.workflow.runtime.WorkflowRuntimeLoop;
import org.redisson.api.RedissonClient;
import org.springframework.context.SmartLifecycle;

import java.lang.System.Logger;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns TaskX JDBC / Redis clients inside the engine so the application DataSource is not displaced.
 */
public final class TaskxExecutorEngine implements SmartLifecycle, AutoCloseable {

    private static final Logger LOG = System.getLogger(TaskxExecutorEngine.class.getName());

    private final TaskxProperties properties;
    private final HikariDataSource dataSource;
    private final RedissonClient redisson;
    private final ThreadPoolExecutor workers;
    private final ExecutorRuntime runtime;
    private final WorkflowRuntimeLoop workflowRuntime;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public TaskxExecutorEngine(
            TaskxProperties properties,
            TaskHandlerRegistry handlers,
            WorkflowHandlerRegistry workflowHandlers
    ) {
        this.properties = properties;
        SlotConfig slots = new SlotConfig(properties.slotCount());
        LockSettings lockSettings = LockSettings.DEFAULT;
        TaskxProperties.Executor executor = properties.executor();

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(properties.mysql().url());
        hikari.setUsername(properties.mysql().user());
        hikari.setPassword(properties.mysql().password());
        hikari.setMaximumPoolSize(8);
        hikari.setPoolName("taskx-executor");
        this.dataSource = new HikariDataSource(hikari);

        this.redisson = RedissonClients.singleServer(properties.redis().address(), lockSettings);
        var lock = new RedissonDistributedLock(redisson, lockSettings);
        var triggerIndex = new RedissonTriggerIndex(redisson);
        var clock = new RedisEpochClock(redisson);
        var calculator = new NextFireCalculator();
        var tasks = new JdbcTaskRepository(dataSource);
        var executions = new JdbcExecutionRepository(dataSource);
        var ownership = new JdbcSlotOwnershipRepository(dataSource);
        var terminalScores = new LockingTerminalScoreWriter(lock, triggerIndex);
        this.workers = new ThreadPoolExecutor(
                executor.workerThreads(),
                executor.workerThreads(),
                0,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(executor.queueCapacity())
        );
        var mapper = new ObjectMapper();
        var workflowDefinitions = new JdbcWorkflowDefinitionStore(dataSource);
        var workflowStore = new JdbcWorkflowRuntimeStore(dataSource);
        var workflowReady = new RedissonWorkflowReadyIndex(redisson);
        var workflowExecutors = new WorkflowNodeExecutorRegistry(List.of(
                new HandlerNodeExecutor(workflowHandlers),
                new HttpNodeExecutor(HttpClient.newHttpClient(), mapper),
                new ConditionNodeExecutor()
        ));
        var workflowEngine = new WorkflowEngine(
                workflowDefinitions,
                workflowStore,
                workflowReady,
                new WorkflowDefinitionCodec(mapper),
                workflowExecutors,
                mapper,
                slots,
                Clock.systemUTC(),
                Duration.ofMinutes(2),
                new FixedDelayWorkflowCompletionListener(
                        executions,
                        tasks,
                        clock,
                        calculator,
                        slots,
                        terminalScores
                ),
                workers
        );
        var dispatch = new Dispatch(
                executor.id(),
                slots,
                clock,
                calculator,
                tasks,
                executions,
                handlers,
                new EngineWorkflowLauncher(workflowEngine),
                workers,
                terminalScores
        );
        var triggerLoop = new TriggerLoop(
                executor.id(),
                lock,
                ownership,
                triggerIndex,
                tasks,
                clock,
                calculator,
                dispatch,
                TriggerLoop.DEFAULT_DUE_LIMIT
        );
        this.runtime = new ExecutorRuntime(
                executor.id(),
                executor.slots(),
                ownership,
                ownership,
                triggerLoop,
                new RecoverLoop(executor.id(), executions, dispatch),
                Executors.newScheduledThreadPool(2)
        );
        this.workflowRuntime = new WorkflowRuntimeLoop(
                executor.id(),
                executor.slots(),
                executor.workerGroups(),
                ownership,
                workflowEngine,
                new WorkflowOutboxPublisher(workflowStore, workflowReady),
                Executors.newScheduledThreadPool(3)
        );
    }

    @Override
    public boolean isAutoStartup() {
        return properties.executor().autoStart();
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        runtime.start();
        workflowRuntime.start();
        LOG.log(
                Logger.Level.INFO,
                "taskx executor {0} started slots={1}",
                properties.executor().id(),
                properties.executor().slots()
        );
    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public boolean isRunning() {
        return running.get() && !closed.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        running.set(false);
        workflowRuntime.close();
        runtime.close();
        workers.shutdownNow();
        redisson.shutdown();
        dataSource.close();
        LOG.log(Logger.Level.INFO, "taskx executor {0} stopped", properties.executor().id());
    }
}
