package io.taskx.admin.web;

import io.taskx.admin.web.dto.HealthResponse;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DataSource dataSource;
    private final RedissonClient redisson;

    public HealthController(DataSource dataSource, RedissonClient redisson) {
        this.dataSource = dataSource;
        this.redisson = redisson;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        String mysql = pingMysql();
        String redis = pingRedis();
        boolean ok = "ok".equals(mysql) && "ok".equals(redis);
        HealthResponse body = new HealthResponse(ok ? "ok" : "degraded", mysql, redis);
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private String pingMysql() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "ok" : "invalid";
        } catch (Exception ex) {
            return ex.getMessage();
        }
    }

    private String pingRedis() {
        try {
            redisson.getBucket("taskx:admin:health").get();
            return "ok";
        } catch (Exception ex) {
            return ex.getMessage();
        }
    }
}
