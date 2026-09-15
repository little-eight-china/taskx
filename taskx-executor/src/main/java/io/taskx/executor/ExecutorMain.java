package io.taskx.executor;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.taskx.common.lock.LockSettings;
import io.taskx.core.config.TaskConfigService;
import io.taskx.core.domain.Task;
import io.taskx.core.domain.Trigger;
import io.taskx.core.handler.MapTaskHandlerRegistry;
import io.taskx.core.poll.Dispatch;
import io.taskx.core.poll.LockingTerminalScoreWriter;
import io.taskx.core.poll.RecoverLoop;
import io.taskx.core.poll.TriggerLoop;
import io.taskx.core.runtime.ExecutorRuntime;
import io.taskx.core.schedule.NextFireCalculator;
import io.taskx.core.slot.SlotConfig;
import io.taskx.meta.config.JdbcRedisTaskConfigService;
import io.taskx.meta.jdbc.JdbcExecutionRepository;
import io.taskx.meta.jdbc.JdbcSlotOwnershipRepository;
import io.taskx.meta.jdbc.JdbcTaskRepository;
import io.taskx.meta.redis.RedisEpochClock;
import io.taskx.meta.redis.RedissonClients;
import io.taskx.meta.redis.RedissonDistributedLock;
import io.taskx.meta.redis.RedissonTriggerIndex;
import org.redisson.api.RedissonClient;

import java.lang.System.Logger;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class ExecutorMain {

    private static final Logger LOG = System.getLogger(ExecutorMain.class.getName());

    private ExecutorMain() {
    }

    public static void main(String[] args) {
        ExecutorSettings settings = ExecutorSettings.fromEnv();
        SlotConfig slots = new SlotConfig(settings.slotCount());
        LockSettings lockSettings = LockSettings.DEFAULT;

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(settings.mysqlUrl());
        hikari.setUsername(settings.mysqlUser());
        hikari.setPassword(settings.mysqlPassword());
        hikari.setMaximumPoolSize(8);

        HikariDataSource dataSource = new HikariDataSource(hikari);
        RedissonClient redisson = RedissonClients.singleServer(settings.redisAddress(), lockSettings);
        var lock = new RedissonDistributedLock(redisson, lockSettings);
        var triggerIndex = new RedissonTriggerIndex(redisson);
        var clock = new RedisEpochClock(redisson);
        var calculator = new NextFireCalculator();
        var tasks = new JdbcTaskRepository(dataSource);
        var executions = new JdbcExecutionRepository(dataSource);
        var ownership = new JdbcSlotOwnershipRepository(dataSource);
        TaskConfigService configs = new JdbcRedisTaskConfigService(
                dataSource, tasks, lock, triggerIndex, clock, calculator, slots);

        if (settings.seedDemoTask()) {
            configs.save(new Task(
                    "demo",
                    "demo",
                    null,
                    true,
                    new Trigger.FixedRate(60),
                    null
            ));
            LOG.log(Logger.Level.INFO, "seeded task id=demo FIXED_RATE 60s");
        }

        var handlers = new MapTaskHandlerRegistry().put("demo", (task, execution) ->
                LOG.log(Logger.Level.INFO, "handled {0} fireTime={1}", task.id(), execution.scheduledFireTime()));

        var workers = new ThreadPoolExecutor(
                4, 4, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(256));
        var dispatch = new Dispatch(
                settings.executorId(),
                slots,
                clock,
                calculator,
                tasks,
                executions,
                handlers,
                workers,
                new LockingTerminalScoreWriter(lock, triggerIndex)
        );
        var triggerLoop = new TriggerLoop(
                settings.executorId(),
                lock,
                ownership,
                triggerIndex,
                tasks,
                clock,
                calculator,
                dispatch,
                TriggerLoop.DEFAULT_DUE_LIMIT
        );
        var recoverLoop = new RecoverLoop(settings.executorId(), executions, dispatch);
        var runtime = new ExecutorRuntime(
                settings.executorId(),
                settings.slots(),
                ownership,
                ownership,
                triggerLoop,
                recoverLoop,
                Executors.newScheduledThreadPool(2)
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            runtime.close();
            workers.shutdownNow();
            redisson.shutdown();
            dataSource.close();
        }));

        runtime.start();
        LOG.log(Logger.Level.INFO, "executor {0} started slots={1}", settings.executorId(), settings.slots());
    }
}
