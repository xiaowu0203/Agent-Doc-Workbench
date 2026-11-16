package com.agentdoc.auth.health;

import com.agentdoc.common.config.WorkbenchAuthProperties;
import com.agentdoc.common.enums.AuthenticationMode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/** 就绪依赖：两模式均需业务数据库，仅local认证要求Redis；不将依赖纳入存活探针。 */
@Component("authenticationReadiness")
@RequiredArgsConstructor
public class AuthenticationReadinessHealthIndicator implements HealthIndicator {
    private final DataSource database;
    private final StringRedisTemplate redis;
    private final WorkbenchAuthProperties properties;

    @Override
    public Health health() {
        try (var connection = database.getConnection()) {
            if (!connection.isValid(1)) { return Health.down().build(); }
        } catch (Exception unavailable) { return Health.down().build(); }
        if (properties.getMode() == AuthenticationMode.LOCAL) {
            try (var connection = redis.getConnectionFactory().getConnection()) {
                if (!"PONG".equals(connection.ping())) { return Health.down().build(); }
            } catch (Exception unavailable) { return Health.down().build(); }
        }
        return Health.up().build();
    }
}
