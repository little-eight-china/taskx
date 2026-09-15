package io.taskx.core.schedule;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.taskx.core.domain.Trigger;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Next fire time from Redis "now" (Unix seconds, UTC). Never backfills missed ticks.
 *
 * <p>Cron is Spring 6-field ({@code second minute hour day month weekday}).
 */
public final class NextFireCalculator {

    private static final CronParser SPRING_CRON =
            new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING));

    public OptionalLong firstFire(Trigger trigger, long nowEpochSecond) {
        Objects.requireNonNull(trigger, "trigger");
        return switch (trigger) {
            case Trigger.Cron cron -> nextCronAfter(cron.expression(), nowEpochSecond);
            case Trigger.FixedRate rate -> OptionalLong.of(nowEpochSecond + rate.intervalSeconds());
            case Trigger.FixedDelay delay -> OptionalLong.of(nowEpochSecond + delay.delaySeconds());
            case Trigger.Delay delay -> OptionalLong.of(nowEpochSecond + delay.delaySeconds());
            case Trigger.Once once -> OptionalLong.of(once.fireEpochSecond());
        };
    }

    /**
     * After claiming a due member: write this score (or empty to ZREM / park until terminal).
     */
    public OptionalLong nextOnClaim(Trigger trigger, long nowEpochSecond) {
        Objects.requireNonNull(trigger, "trigger");
        return switch (trigger) {
            case Trigger.Cron cron -> nextCronAfter(cron.expression(), nowEpochSecond);
            case Trigger.FixedRate rate -> OptionalLong.of(nowEpochSecond + rate.intervalSeconds());
            case Trigger.FixedDelay ignored -> OptionalLong.empty();
            case Trigger.Delay ignored -> OptionalLong.empty();
            case Trigger.Once ignored -> OptionalLong.empty();
        };
    }

    /**
     * After a terminal execution. Only {@code FIXED_DELAY} writes a new score from now.
     */
    public OptionalLong nextOnTerminal(Trigger trigger, long nowEpochSecond) {
        Objects.requireNonNull(trigger, "trigger");
        return switch (trigger) {
            case Trigger.FixedDelay delay -> OptionalLong.of(nowEpochSecond + delay.delaySeconds());
            default -> OptionalLong.empty();
        };
    }

    private OptionalLong nextCronAfter(String expression, long nowEpochSecond) {
        Cron cron;
        try {
            cron = SPRING_CRON.parse(expression);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid Spring cron expression: " + expression, ex);
        }
        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        ZonedDateTime now = Instant.ofEpochSecond(nowEpochSecond).atZone(ZoneOffset.UTC);
        Optional<ZonedDateTime> next = executionTime.nextExecution(now);
        if (next.isEmpty()) {
            return OptionalLong.empty();
        }
        long epoch = next.get().toEpochSecond();
        if (epoch > nowEpochSecond) {
            return OptionalLong.of(epoch);
        }
        return executionTime.nextExecution(now.plusSeconds(1))
                .map(time -> OptionalLong.of(time.toEpochSecond()))
                .orElseGet(OptionalLong::empty);
    }
}
