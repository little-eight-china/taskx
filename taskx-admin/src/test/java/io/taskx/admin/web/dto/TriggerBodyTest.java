package io.taskx.admin.web.dto;

import io.taskx.core.domain.Trigger;
import io.taskx.core.domain.TriggerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TriggerBodyTest {

    @Test
    void mapsEachTriggerType() {
        assertInstanceOf(Trigger.Cron.class,
                new TriggerBody(TriggerType.CRON, "0 * * * * *", null, null, null).toTrigger());
        assertEquals(60, ((Trigger.FixedRate) new TriggerBody(TriggerType.FIXED_RATE, null, 60, null, null).toTrigger())
                .intervalSeconds());
        assertEquals(15, ((Trigger.FixedDelay) new TriggerBody(TriggerType.FIXED_DELAY, null, null, 15, null).toTrigger())
                .delaySeconds());
        assertEquals(10, ((Trigger.Delay) new TriggerBody(TriggerType.DELAY, null, null, 10, null).toTrigger())
                .delaySeconds());
        assertEquals(1_700_000_000L,
                ((Trigger.Once) new TriggerBody(TriggerType.ONCE, null, null, null, 1_700_000_000L).toTrigger())
                        .fireEpochSecond());
    }

    @Test
    void rejectsMissingType() {
        assertThrows(IllegalArgumentException.class,
                () -> new TriggerBody(null, null, 60, null, null).toTrigger());
    }
}
