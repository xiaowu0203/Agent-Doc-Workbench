package com.agentdoc.auth.service;

import com.agentdoc.auth.config.JwtProperties;
import com.agentdoc.common.constant.RedisKeyConstants;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Refresh Token 存储服务
 * 以 Redis 维护 refresh‑token ↔ userId 双向映射，实现令牌轮换、会话撤销、单设备登录控制
 * <p>键空间约定：
 * <ul>
 *     <li>token 主映射：{@code agent-doc-workbench:auth:refresh:{token} -> userId}（{@link RedisKeyConstants#REFRESH_TOKEN_PREFIX}）</li>
 *     <li>用户索引：{@code agent-doc-workbench:auth:refresh:user:{userId} -> token}（{@link RedisKeyConstants#REFRESH_TOKEN_USER_INDEX_PREFIX}），
 *         与 token 键空间隔离，杜绝随机 token 撞上数字 userId 键名冲突问题</li>
 * </ul>
 * <p>业务模式：单设备登录，颁发新 refreshToken 会自动使该用户旧 refreshToken 失效
 */
@Slf4j
@Service
public class RefreshTokenService {
    // refreshToken主key前缀：key=refreshToken, value=userId
    private static final String KEY_PREFIX = RedisKeyConstants.REFRESH_TOKEN_PREFIX;
    // 用户反向索引key前缀：key=userId, value=refreshToken，用于通过用户ID快速拿到当前token
    private static final String USER_INDEX_PREFIX = RedisKeyConstants.REFRESH_TOKEN_USER_INDEX_PREFIX;
    private final StringRedisTemplate redisTemplate;
    // refreshToken过期时间，从配置文件读取
    private final Duration refreshTtl;

    public RefreshTokenService(StringRedisTemplate redisTemplate, JwtProperties props) {
        this.redisTemplate = redisTemplate;
        this.refreshTtl = props.refreshTtl();
    }

    /**
     * 存储refreshToken，单设备模式：颁发新token，自动覆盖作废该用户旧token
     * 维护双向映射：token->userId 、 userId->token，同时设置相同TTL过期时间
     * @param refreshToken 新生成的刷新令牌
     * @param userId 用户ID
     */
    public void store(String refreshToken, Long userId) {
        // 查询用户当前正在使用的旧token
        String oldToken = redisTemplate.opsForValue().get(USER_INDEX_PREFIX + userId);
        if (StringUtils.isNotBlank(oldToken)) {
            // 删除旧token对应的主映射，旧refreshToken直接失效
            redisTemplate.delete(KEY_PREFIX + oldToken);
        }
        // 写入新token -> userId 映射，带过期时间
        redisTemplate.opsForValue().set(KEY_PREFIX + refreshToken, String.valueOf(userId), refreshTtl);
        // 写入反向索引 userId -> refreshToken，带相同过期时间
        redisTemplate.opsForValue().set(USER_INDEX_PREFIX + userId, refreshToken, refreshTtl);
    }

    /**
     /**
     * 校验refreshToken是否有效，并返回对应用户ID
     * @param refreshToken 待校验刷新令牌
     * @return 有效返回userId；token不存在/已过期/已撤销返回null
     */
    public Long validateAndGetUserId(String refreshToken) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + refreshToken);
        if (value == null) {
            return null;
        }
        return Long.valueOf(value);
    }

    /**
     * 根据用户ID获取当前有效的refreshToken
     * @param userId 用户ID
     * @return 返回当前生效refreshToken；无有效令牌返回null
     */
    public String validateAndGetRefreshToken(Long userId) {
        return redisTemplate.opsForValue().get(USER_INDEX_PREFIX + userId);
    }

    /**
     * 根据refreshToken撤销会话（登出操作：传入当前持有的refreshToken）
     * 双向删除：删除token主key，同时删除该用户的反向索引key
     * @param refreshToken 需要撤销的刷新令牌
     */
    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        // 通过token拿到对应的userId
        String userId = redisTemplate.opsForValue().get(KEY_PREFIX + refreshToken);
        // 删除token主映射
        redisTemplate.delete(KEY_PREFIX + refreshToken);
        // 删除userId反向索引
        if (StringUtils.isNotBlank(userId)) {
            redisTemplate.delete(USER_INDEX_PREFIX + userId);
        }
    }

    /**
     * 根据用户ID撤销该用户全部会话
     * 使用场景：修改密码、账号强制下线、管理员踢用户下线
     * @param userId 用户ID
     */
    public void revoke(Long userId) {
        if (userId == null) {
            return;
        }
        // 通过用户索引拿到当前token
        String token = redisTemplate.opsForValue().get(USER_INDEX_PREFIX + userId);
        // 删除用户反向索引key
        redisTemplate.delete(USER_INDEX_PREFIX + userId);
        // 删除token主key，令牌直接作废
        if (StringUtils.isNotBlank(token)) {
            redisTemplate.delete(KEY_PREFIX + token);
        }
    }
}