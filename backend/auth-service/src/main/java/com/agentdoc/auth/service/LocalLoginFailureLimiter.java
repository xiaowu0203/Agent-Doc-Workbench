package com.agentdoc.auth.service;

import com.agentdoc.auth.config.LocalIdentityProperties;
import com.agentdoc.auth.constant.LocalIdentityConstant;
import com.agentdoc.auth.utils.AuthenticationPrivacyUtils;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** 登录失败/校验中请求共用滑动窗口；Redis故障拒绝，不降级放行。 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalLoginFailureLimiter {
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - tonumber(ARGV[2]))
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now - tonumber(ARGV[4]))
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[1]) or redis.call('ZCARD', KEYS[2]) >= tonumber(ARGV[3]) then
                return 0
            end
            redis.call('ZADD', KEYS[1], now, ARGV[5])
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            redis.call('ZADD', KEYS[2], now, ARGV[5])
            redis.call('PEXPIRE', KEYS[2], ARGV[4])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final String namespace;
    private final int accountLimit;
    private final Duration accountWindow;
    private final int ipLimit;
    private final Duration ipWindow;

    @Autowired
    public LocalLoginFailureLimiter(StringRedisTemplate redis, LocalIdentityProperties properties) {
        this(redis, properties.getLoginRateNamespace(), LocalIdentityConstant.ACCOUNT_FAILURE_LIMIT,
                LocalIdentityConstant.ACCOUNT_FAILURE_WINDOW, LocalIdentityConstant.IP_FAILURE_LIMIT,
                LocalIdentityConstant.IP_FAILURE_WINDOW);
    }

    LocalLoginFailureLimiter(StringRedisTemplate redis, String namespace, int accountLimit,
                             Duration accountWindow, int ipLimit, Duration ipWindow) {
        if (namespace == null || !namespace.matches("[a-zA-Z0-9:_-]{1,128}")) {
            throw new IllegalArgumentException("登录限流键空间无效");
        }
        this.redis = redis; this.namespace = namespace; this.accountLimit = accountLimit;
        this.accountWindow = accountWindow; this.ipLimit = ipLimit; this.ipWindow = ipWindow;
    }

    /** 规范化IP由受信BFF边界提供；调用方不向浏览器暴露预占标识。 */
    public Reservation reserve(String username, String normalizedIp) {
        List<String> keys = List.of(namespace + ":account:" + AuthenticationPrivacyUtils.digest(AuthenticationPrivacyUtils.normalizeUsername(username)),
                namespace + ":ip:" + AuthenticationPrivacyUtils.digest(normalizedIp));
        String attempt = UUID.randomUUID().toString();
        Long result = execute(RESERVE, keys, Integer.toString(accountLimit), Long.toString(accountWindow.toMillis()),
                Integer.toString(ipLimit), Long.toString(ipWindow.toMillis()), attempt);
        if (result == 0) { throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后重试"); }
        if (result != 1) { throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE); }
        return new Reservation(keys, attempt);
    }

    /** 密码/状态校验成功后只撤回自己的预占，不抹掉并发及历史失败。 */
    public void release(Reservation reservation) {
        if (execute(RELEASE, reservation.keys(), reservation.attemptId()) != 1) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private Long execute(DefaultRedisScript<Long> script, List<String> keys, String... arguments) {
        try {
            Long result = redis.execute(script, keys, (Object[]) arguments);
            if (result == null) { throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE); }
            return result;
        } catch (DataAccessException error) {
            log.warn("登录限流存储不可用 type={}", error.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /** @param keys 仅含规范化身份哈希的Redis键 @param attemptId Auth生成的本次随机预占标识 */
    public record Reservation(List<String> keys, String attemptId) { }
}
