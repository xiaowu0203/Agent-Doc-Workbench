package com.agentdoc.auth.config;

import com.agentdoc.auth.health.AuthenticationReadinessHealthIndicator;
import com.agentdoc.common.config.WorkbenchAuthProperties;
import com.agentdoc.common.enums.AuthenticationMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthenticationReadinessHealthIndicatorTest {
    @Test
    void localRedisFailureRemovesReadinessAndRecoveryRestoresIt() throws Exception {
        var database = mock(DataSource.class); var sql = mock(Connection.class);
        when(database.getConnection()).thenReturn(sql); when(sql.isValid(1)).thenReturn(true);
        var redis = mock(StringRedisTemplate.class); var factory = mock(RedisConnectionFactory.class); var connection = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory); when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG", null, "PONG");
        var health = new AuthenticationReadinessHealthIndicator(database, redis, new WorkbenchAuthProperties());
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
        assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
        verify(connection, times(3)).close(); verify(sql, times(3)).close();
    }

    @Test
    void oidcDoesNotProbeRedisAndDatabaseFailureRemovesReadinessWithoutLeakingDetails() throws Exception {
        var database = mock(DataSource.class); var sql = mock(Connection.class); var redis = mock(StringRedisTemplate.class);
        var properties = new WorkbenchAuthProperties(); properties.setMode(AuthenticationMode.OIDC);
        when(database.getConnection()).thenReturn(sql); when(sql.isValid(1)).thenReturn(true);
        var health = new AuthenticationReadinessHealthIndicator(database, redis, properties);
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
        when(database.getConnection()).thenThrow(new SQLException("private database url"));
        assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.health().getDetails()).isEmpty(); verifyNoInteractions(redis);
    }
}
