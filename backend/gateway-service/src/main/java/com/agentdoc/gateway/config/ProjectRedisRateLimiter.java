package com.agentdoc.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.AbstractRateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 项目自定义 Redis 令牌桶限流器（RateLimiter）。
 * <p>
 * 背景：Spring Cloud Gateway 4.3.0（2025.0.0）移除了 RedisRateLimiter 的 key-prefix 配置，
 * 键前缀硬编码为 {@code request_rate_limiter}（字节码级），导致共享 Redis 实例上本项目限流键
 * 与其他项目可能冲突。本实现复制框架令牌桶 Lua 逻辑，仅将键前缀改为 {@code agent-doc-workbench:rate}，
 * 键格式：{@code agent-doc-workbench:rate.{routeId.id}.{tokens,timestamp}}（保留 id 维度按客户端分桶，
 * {@code {routeId.id}} 为 Redis hash tag，保证两个键同 slot 供 Lua 原子操作）。
 * <p>
 * 配置兼容：复用 {@link RedisRateLimiter.Config}，filter args（redis-rate-limiter.replenishRate /
 * burstCapacity / requestedTokens / key-resolver）照常生效。以 @Primary 替换框架默认 RedisRateLimiter。
 */
@Slf4j
public class ProjectRedisRateLimiter extends AbstractRateLimiter<RedisRateLimiter.Config>
        implements RateLimiter<RedisRateLimiter.Config>, ApplicationContextAware {

    /** Redis 键前缀（工程级隔离） */
    public static final String KEY_PREFIX = "agent-doc-workbench:rate";

    private final ReactiveStringRedisTemplate redisTemplate;
    private RedisScript<List> script;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private boolean includeHeaders = true;
    private String remainingHeader = "X-RateLimit-Remaining";
    private String replenishRateHeader = "X-RateLimit-Replenish-Rate";
    private String burstCapacityHeader = "X-RateLimit-Burst-Capacity";
    private String requestedTokensHeader = "X-RateLimit-Requested-Tokens";

    public ProjectRedisRateLimiter(ReactiveStringRedisTemplate redisTemplate, RedisScript<List> script) {
        super(RedisRateLimiter.Config.class, "redis-rate-limiter", null);
        this.redisTemplate = redisTemplate;
        this.script = script;
    }

    @Override
    public void setApplicationContext(ApplicationContext context) {
        if (this.initialized.compareAndSet(false, true)) {
            if (context.getBeanNamesForType(ConfigurationService.class).length > 0) {
                setConfigurationService(context.getBean(ConfigurationService.class));
            }
        }
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        if (!this.initialized.get()) {
            throw new IllegalStateException("ProjectRedisRateLimiter is not initialized");
        }

        RedisRateLimiter.Config routeConfig = loadConfiguration(routeId);
        int replenishRate = routeConfig.getReplenishRate();
        int burstCapacity = routeConfig.getBurstCapacity();
        int requestedTokens = routeConfig.getRequestedTokens();

        List<String> keys = getKeys(id, routeId);
        List<String> scriptArgs = Arrays.asList(
                replenishRate + "",
                burstCapacity + "",
                Instant.now().getEpochSecond() + "",
                requestedTokens + "");

        @SuppressWarnings("unchecked")
        Flux<List<Long>> flux = (Flux<List<Long>>) (Flux<?>) this.redisTemplate.execute(this.script, keys, scriptArgs);
        return flux.onErrorResume(throwable -> {
            log.error("Error calling rate limiter lua", throwable);
            return Flux.just(Arrays.asList(1L, -1L));
        }).next().map(results -> {
            boolean allowed = results.get(0) == 1L;
            Long tokensLeft = results.get(1);
            Response response = new Response(allowed, getHeaders(routeConfig, tokensLeft));
            if (log.isDebugEnabled()) {
                log.debug("response: " + response);
            }
            return response;
        });
    }

    /**
     * 键生成：{@code agent-doc-workbench:rate.{routeId.id}.{tokens,timestamp}}。
     */
    private List<String> getKeys(String id, String routeId) {
        String prefix = KEY_PREFIX + ".{" + routeId + "." + id + "}.";
        return Arrays.asList(prefix + "tokens", prefix + "timestamp");
    }

    private RedisRateLimiter.Config loadConfiguration(String routeId) {
        RedisRateLimiter.Config routeConfig = getConfig().get(routeId);
        if (routeConfig == null) {
            routeConfig = getConfig().get("defaultFilters");
        }
        if (routeConfig == null) {
            throw new IllegalArgumentException("No Configuration found for route " + routeId + " or defaultFilters");
        }
        return routeConfig;
    }

    public Map<String, String> getHeaders(RedisRateLimiter.Config config, Long tokensLeft) {
        Map<String, String> headers = new HashMap<>();
        if (isIncludeHeaders()) {
            headers.put(this.remainingHeader, tokensLeft.toString());
            headers.put(this.replenishRateHeader, String.valueOf(config.getReplenishRate()));
            headers.put(this.burstCapacityHeader, String.valueOf(config.getBurstCapacity()));
            headers.put(this.requestedTokensHeader, String.valueOf(config.getRequestedTokens()));
        }
        return headers;
    }

    public boolean isIncludeHeaders() {
        return includeHeaders;
    }

    public void setIncludeHeaders(boolean includeHeaders) {
        this.includeHeaders = includeHeaders;
    }

    public String getRemainingHeader() {
        return remainingHeader;
    }

    public void setRemainingHeader(String remainingHeader) {
        this.remainingHeader = remainingHeader;
    }

    public String getReplenishRateHeader() {
        return replenishRateHeader;
    }

    public void setReplenishRateHeader(String replenishRateHeader) {
        this.replenishRateHeader = replenishRateHeader;
    }

    public String getBurstCapacityHeader() {
        return burstCapacityHeader;
    }

    public void setBurstCapacityHeader(String burstCapacityHeader) {
        this.burstCapacityHeader = burstCapacityHeader;
    }

    public String getRequestedTokensHeader() {
        return requestedTokensHeader;
    }

    public void setRequestedTokensHeader(String requestedTokensHeader) {
        this.requestedTokensHeader = requestedTokensHeader;
    }
}
