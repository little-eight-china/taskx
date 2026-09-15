package io.taskx.meta.json;

import io.taskx.core.domain.Trigger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TriggerCodecTest {

    @Test
    void roundTripEachType() {
        assertEquals(new Trigger.Cron("0 0 * * * *"),
                TriggerCodec.from("CRON", TriggerCodec.toSpecJson(new Trigger.Cron("0 0 * * * *"))));
        assertEquals(new Trigger.FixedRate(60),
                TriggerCodec.from("FIXED_RATE", TriggerCodec.toSpecJson(new Trigger.FixedRate(60))));
        assertEquals(new Trigger.FixedDelay(15),
                TriggerCodec.from("FIXED_DELAY", TriggerCodec.toSpecJson(new Trigger.FixedDelay(15))));
        assertEquals(new Trigger.Delay(9),
                TriggerCodec.from("DELAY", TriggerCodec.toSpecJson(new Trigger.Delay(9))));
        assertEquals(new Trigger.Once(42L),
                TriggerCodec.from("ONCE", TriggerCodec.toSpecJson(new Trigger.Once(42L))));
    }
}
