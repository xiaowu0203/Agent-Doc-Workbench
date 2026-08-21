package com.agentdoc.common.utils;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisUtilsTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> template = mock(RedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> valueOps = mock(ValueOperations.class);

    @Test
    void setIfAbsentDelegatesWithTimeout() {
        when(template.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("lock:key"), eq("owner"), any(Duration.class))).thenReturn(true);

        RedisUtils utils = new RedisUtils(template);
        assertTrue(utils.setIfAbsent("lock:key", "owner", Duration.ofSeconds(10)));

        verify(valueOps).setIfAbsent(eq("lock:key"), eq("owner"), any(Duration.class));
    }

    @Test
    void incrementReturnsZeroWhenRedisReturnsNull() {
        when(template.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("counter", 1)).thenReturn(null);

        RedisUtils utils = new RedisUtils(template);
        assertEquals(0L, utils.increment("counter", 1));
    }
}
