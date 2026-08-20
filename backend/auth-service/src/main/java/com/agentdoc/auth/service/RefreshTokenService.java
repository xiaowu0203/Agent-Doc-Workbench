package com.agentdoc.auth.service;

import com.agentdoc.auth.security.JwtProperties;
import com.agentdoc.common.constant.RedisKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Refresh Token 存储：以 Redis 记录随机值到用户 ID 的映射，支持撤销与轮换。
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = RedisKeyConstants.REFRESH_TOKEN_PREFIX;
    private final StringRedisTemplate redisTemplate;
    private final Duration refreshTtl;

    public RefreshTokenService(StringRedisTemplate redisTemplate, JwtProperties props) {
        this.redisTemplate = redisTemplate;
        this.refreshTtl = props.refreshTtl();
    }

    public void store(String refreshToken, Long userId) {
        redisTemplate.opsForValue().set(KEY_PREFIX + refreshToken, String.valueOf(userId), refreshTtl);
    }

    /**
     * 校验并返回用户 ID；无效或不存在返回 null。
     */
    public Long validateAndGetUserId(String refreshToken) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + refreshToken);
        if (value == null) {
            return null;
        }
        return Long.valueOf(value);
    }

    /**
     * 撤销 Refresh Token（登录/轮换时使用）。
     */
    public void revoke(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            redisTemplate.delete(KEY_PREFIX + refreshToken);
        }
    }
}