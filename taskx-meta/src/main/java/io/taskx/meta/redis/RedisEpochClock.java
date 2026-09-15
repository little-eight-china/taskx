package io.taskx.meta.redis;

import io.taskx.core.clock.EpochClock;
import io.taskx.meta.MetaException;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.Objects;

public final class RedisEpochClock implements EpochClock {

    private static final String TIME_LUA = "return redis.call('TIME')";

    private final RedissonClient redisson;

    public RedisEpochClock(RedissonClient redisson) {
        this.redisson = Objects.requireNonNull(redisson);
    }

    @Override
    public long nowEpochSecond() {
        List<Object> time = redisson.getScript().eval(
                RScript.Mode.READ_ONLY,
                TIME_LUA,
                RScript.ReturnType.MULTI,
                List.of()
        );
        if (time == null || time.isEmpty()) {
            throw new MetaException("Redis TIME returned empty");
        }
        return Long.parseLong(String.valueOf(time.getFirst()));
    }
}
