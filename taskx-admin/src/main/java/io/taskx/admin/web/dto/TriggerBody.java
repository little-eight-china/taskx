package io.taskx.admin.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.taskx.core.domain.Trigger;
import io.taskx.core.domain.TriggerType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TriggerBody(
        TriggerType type,
        String expression,
        Integer intervalSeconds,
        Integer delaySeconds,
        Long fireEpochSecond
) {

    public static TriggerBody from(Trigger trigger) {
        return switch (trigger) {
            case Trigger.Cron cron -> new TriggerBody(TriggerType.CRON, cron.expression(), null, null, null);
            case Trigger.FixedRate rate -> new TriggerBody(TriggerType.FIXED_RATE, null, rate.intervalSeconds(), null, null);
            case Trigger.FixedDelay delay ->
                    new TriggerBody(TriggerType.FIXED_DELAY, null, null, delay.delaySeconds(), null);
            case Trigger.Delay delay -> new TriggerBody(TriggerType.DELAY, null, null, delay.delaySeconds(), null);
            case Trigger.Once once -> new TriggerBody(TriggerType.ONCE, null, null, null, once.fireEpochSecond());
        };
    }

    public Trigger toTrigger() {
        if (type == null) {
            throw new IllegalArgumentException("trigger.type is required");
        }
        return switch (type) {
            case CRON -> new Trigger.Cron(expression);
            case FIXED_RATE -> new Trigger.FixedRate(requirePositive(intervalSeconds, "intervalSeconds"));
            case FIXED_DELAY -> new Trigger.FixedDelay(requirePositive(delaySeconds, "delaySeconds"));
            case DELAY -> new Trigger.Delay(requirePositive(delaySeconds, "delaySeconds"));
            case ONCE -> new Trigger.Once(requireEpoch(fireEpochSecond));
        };
    }

    private static int requirePositive(Integer value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static long requireEpoch(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("fireEpochSecond is required");
        }
        return value;
    }
}
