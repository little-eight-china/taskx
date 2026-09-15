package io.taskx.meta.redis;

import io.taskx.common.lock.LockSettings;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.Objects;

public final class RedissonClients {

    private RedissonClients() {
    }

    public static RedissonClient singleServer(String address, LockSettings settings) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(settings);
        Config config = new Config();
        config.useSingleServer().setAddress(address);
        config.setLockWatchdogTimeout(settings.ttl().toMillis());
        return Redisson.create(config);
    }
}
