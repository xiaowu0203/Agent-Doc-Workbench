package com.agentdoc.auth.service;

import com.agentdoc.auth.config.LocalIdentityProperties;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalLoginFailureLimiterTest {
    @Test
    void unavailableRedisCannotBeTreatedAsAnAllowedAttempt() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("fixture sensitive dependency detail"));
        var limiter = new LocalLoginFailureLimiter(redis, new LocalIdentityProperties());
        assertThatThrownBy(() -> limiter.reserve("account", "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode())
                        .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode()))
                .hasMessageNotContaining("fixture sensitive");
    }

    @Test
    void missingOrUnexpectedRedisReplyCannotFailOpen() {
        var redis = mock(StringRedisTemplate.class);
        var limiter = new LocalLoginFailureLimiter(redis, new LocalIdentityProperties());
        assertThatThrownBy(() -> limiter.reserve("account", "127.0.0.1")).isInstanceOf(BusinessException.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(2L);
        assertThatThrownBy(() -> limiter.reserve("account", "127.0.0.1")).isInstanceOf(BusinessException.class);
    }
}
