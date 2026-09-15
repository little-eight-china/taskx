package io.taskx.core.domain;

import io.taskx.core.Require;

/**
 * How a task is scheduled. Time unit is Unix seconds.
 */
public sealed interface Trigger {

    TriggerType type();

    record Cron(String expression) implements Trigger {
        public Cron {
            expression = Require.notBlank(expression, "cron expression");
        }

        @Override
        public TriggerType type() {
            return TriggerType.CRON;
        }
    }

    record FixedRate(int intervalSeconds) implements Trigger {
        public FixedRate {
            intervalSeconds = Require.positive(intervalSeconds, "intervalSeconds");
        }

        @Override
        public TriggerType type() {
            return TriggerType.FIXED_RATE;
        }
    }

    record FixedDelay(int delaySeconds) implements Trigger {
        public FixedDelay {
            delaySeconds = Require.positive(delaySeconds, "delaySeconds");
        }

        @Override
        public TriggerType type() {
            return TriggerType.FIXED_DELAY;
        }
    }

    record Delay(int delaySeconds) implements Trigger {
        public Delay {
            delaySeconds = Require.positive(delaySeconds, "delaySeconds");
        }

        @Override
        public TriggerType type() {
            return TriggerType.DELAY;
        }
    }

    record Once(long fireEpochSecond) implements Trigger {
        @Override
        public TriggerType type() {
            return TriggerType.ONCE;
        }
    }
}
