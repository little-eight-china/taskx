package io.taskx.core.schedule;

import io.taskx.core.domain.Trigger;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NextFireCalculatorTest {

    private final NextFireCalculator calculator = new NextFireCalculator();

    @Test
    void cronIsStrictlyAfterNowUtc() {
        long now = Instant.parse("2026-01-15T10:00:00Z").getEpochSecond();
        OptionalLong next = calculator.nextOnClaim(new Trigger.Cron("0 0 * * * *"), now);
        assertTrue(next.isPresent());
        assertEquals(Instant.parse("2026-01-15T11:00:00Z").getEpochSecond(), next.getAsLong());
        assertEquals(next, calculator.firstFire(new Trigger.Cron("0 0 * * * *"), now));
    }

    @Test
    void cronRejectsInvalidExpression() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.nextOnClaim(new Trigger.Cron("not-a-cron"), 1_000L));
    }

    @Test
    void fixedRateAddsIntervalFromNow() {
        OptionalLong next = calculator.nextOnClaim(new Trigger.FixedRate(60), 1_000L);
        assertEquals(1_060L, next.orElseThrow());
        assertEquals(1_060L, calculator.firstFire(new Trigger.FixedRate(60), 1_000L).orElseThrow());
    }

    @Test
    void fixedDelayWaitsUntilTerminal() {
        Trigger.FixedDelay delay = new Trigger.FixedDelay(30);
        assertTrue(calculator.nextOnClaim(delay, 1_000L).isEmpty());
        assertEquals(1_030L, calculator.nextOnTerminal(delay, 1_000L).orElseThrow());
        assertEquals(1_030L, calculator.firstFire(delay, 1_000L).orElseThrow());
    }

    @Test
    void delayAndOnceDoNotRescheduleOnClaim() {
        assertTrue(calculator.nextOnClaim(new Trigger.Delay(15), 1_000L).isEmpty());
        assertTrue(calculator.nextOnClaim(new Trigger.Once(5_000L), 1_000L).isEmpty());
        assertTrue(calculator.nextOnTerminal(new Trigger.Delay(15), 1_000L).isEmpty());
        assertTrue(calculator.nextOnTerminal(new Trigger.Once(5_000L), 1_000L).isEmpty());
    }

    @Test
    void firstFireForDelayAndOnce() {
        assertEquals(1_015L, calculator.firstFire(new Trigger.Delay(15), 1_000L).orElseThrow());
        assertEquals(5_000L, calculator.firstFire(new Trigger.Once(5_000L), 1_000L).orElseThrow());
    }
}
