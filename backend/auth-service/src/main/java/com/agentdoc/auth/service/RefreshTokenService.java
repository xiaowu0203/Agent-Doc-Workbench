package com.agentdoc.auth.service;

import com.agentdoc.auth.security.JwtProperties;
import com.agentdoc.common.constant.RedisKeyConstants;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Refresh Token 存储：以 Redis 记录随机值到用户 ID 的映射，支持撤销与轮换。
 * <p>键空间约定：
 * <ul>
 *     <li>token 主映射：{@code agent-doc-workbench:auth:refresh:{token} -> userId}（{@link RedisKeyConstants#REFRESH_TOKEN_PREFIX}）</li>
 *     <li>用户索引：{@code agent-doc-workbench:auth:refresh:user:{userId} -> token}（{@link RedisKeyConstants#REFRESH_TOKEN_USER_INDEX_PREFIX}），
 *         与 token 键空间隔离，杜绝随机 token 撞上数字 userId 键</li>
 * </ul>
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = RedisKeyConstants.REFRESH_TOKEN_PREFIX;
    private static final String USER_INDEX_PREFIX = RedisKeyConstants.REFRESH_TOKEN_USER_INDEX_PREFIX;
    private final StringRedisTemplate redisTemplate;
    private final Duration refreshTtl;

    public RefreshTokenService(StringRedisTemplate redisTemplate, JwtProperties props) {
        this.redisTemplate = redisTemplate;
        this.refreshTtl = props.refreshTtl();
    }

    /**
     * 存储refreshToken，单设备模式：新token会覆盖作废用户旧token
     * @param refreshToken 新刷新令牌
     * @param userId 用户id
     */
    public void store(String refreshToken, Long userId) {
        // 先获取用户旧token，做双向删除，作废旧会话（此处只需要删除旧token、因为userId不变，下方有重新set值，但token会变）
        String oldToken = redisTemplate.opsForValue().get(USER_INDEX_PREFIX + userId);
        if (StringUtils.isNotBlank(oldToken)) {
            redisTemplate.delete(KEY_PREFIX + oldToken);
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + refreshToken, String.valueOf(userId), refreshTtl);
        redisTemplate.opsForValue().set(USER_INDEX_PREFIX + userId, refreshToken, refreshTtl);
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
     * 根据userId获取当前有效的refreshToken
     */
    public String validateAndGetRefreshToken(Long userId) {
        return redisTemplate.opsForValue().get(USER_INDEX_PREFIX + userId);
    }

    /**
     * 根据token撤销会话
     */
    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String userId = redisTemplate.opsForValue().get(KEY_PREFIX + refreshToken);
        redisTemplate.delete(KEY_PREFIX + refreshToken);
        // 反向删除userId对应的索引键
        if (StringUtils.isNotBlank(userId)) {
            redisTemplate.delete(USER_INDEX_PREFIX + userId);
        }
    }

    /**
     * 根据用户ID撤销全部会话（登出、改密码场景）
     */
    public void revoke(Long userId) {
        if (userId == null) {
            return;
        }
        String token = redisTemplate.opsForValue().get(USER_INDEX_PREFIX + userId);
        redisTemplate.delete(USER_INDEX_PREFIX + userId);
        if (StringUtils.isNotBlank(token)) {
            redisTemplate.delete(KEY_PREFIX + token);
        }
    }
}