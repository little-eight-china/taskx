package io.taskx.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

@ConfigurationProperties(prefix = "taskx")
public record AdminProperties(
        int slotCount,
        Mysql mysql,
        Redis redis
) {

    public AdminProperties {
        if (slotCount <= 0) {
            throw new IllegalArgumentException("taskx.slot-count must be > 0");
        }
        mysql = Objects.requireNonNull(mysql, "taskx.mysql");
        redis = Objects.requireNonNull(redis, "taskx.redis");
    }

    public record Mysql(String url, String user, String password) {
        public Mysql {
            url = Objects.requireNonNull(url, "taskx.mysql.url");
            user = Objects.requireNonNull(user, "taskx.mysql.user");
            password = password == null ? "" : password;
        }
    }

    public record Redis(String address) {
        public Redis {
            address = Objects.requireNonNull(address, "taskx.redis.address");
        }
    }
}
