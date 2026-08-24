package com.agentdoc.gateway.config;

import com.agentdoc.common.constant.RedisKeyConstants;
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
 * 项目自定义 Redis 令牌桶限流器，替换 Spring Cloud Gateway 原生 {@code RedisRateLimiter}。
 * <p>
 * 背景：Spring Cloud Gateway 4.3.0（2025.0.0）移除 key‑prefix 可配置项，限流Redis键前缀硬编码为
 * {@code request_rate_limiter}。多服务共享同一Redis实例时，不同项目的限流key会发生冲突。
 * 本类完整复用框架原生令牌桶Lua业务逻辑，仅修改Redis键前缀为 {@code agent‑doc‑workbench:rate}。
 * <p>
 * Redis key格式：{@code agent‑doc‑workbench:rate.{routeId.id}.{tokens,timestamp}}
 * 使用 Hash‑Tag {@code {routeId.id}}，保证 tokens、timestamp 两个key落在同一个Redis slot，
 * 满足Lua脚本原子操作要求。
 * <p>
 * 兼容说明：
 * <ul>
 * <li>沿用框架 {@link RedisRateLimiter.Config} 配置模型；</li>
 * <li>yaml filter参数 redis‑rate‑limiter.replenishRate / burstCapacity / requestedTokens / key‑resolver 完全不变；</li>
 * <li>通过 {@code @Primary} 覆盖默认限流器实现，Gateway {@code RequestRateLimiterGatewayFilterFactory} 自动使用本实现；</li>
 * <li>返回限流响应头 X‑RateLimit‑*，客户端可读取令牌剩余、桶容量等信息。</li>
 * </ul>
 */
@Slf4j
public class ProjectRedisRateLimiter extends AbstractRateLimiter<RedisRateLimiter.Config>
        implements RateLimiter<RedisRateLimiter.Config>, ApplicationContextAware {

    private static final int SCRIPT_ALLOWED_INDEX = 0;
    private static final int SCRIPT_TOKENS_LEFT_INDEX = 1;
    private static final long SCRIPT_ALLOWED_VALUE = 1L;
    private static final long UNKNOWN_TOKENS_LEFT = -1L;

    private final ReactiveStringRedisTemplate redisTemplate;
    private RedisScript<List> script;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // 是否输出限流相关响应头
    private boolean includeHeaders = true;
    private String remainingHeader = "X-RateLimit-Remaining";
    private String replenishRateHeader = "X-RateLimit-Replenish-Rate";
    private String burstCapacityHeader = "X-RateLimit-Burst-Capacity";
    private String requestedTokensHeader = "X-RateLimit-Requested-Tokens";

    /**
     * @param redisTemplate WebFlux响应式Redis模板
     * @param script 令牌桶Lua脚本
     */
    public ProjectRedisRateLimiter(ReactiveStringRedisTemplate redisTemplate, RedisScript<List> script) {
        super(RedisRateLimiter.Config.class, "redis-rate-limiter", null);
        this.redisTemplate = redisTemplate;
        this.script = script;
    }

    /**
     * Spring上下文回调，初始化配置服务，保证路由配置加载就绪。
     * @param context Spring应用上下文
     */
    @Override
    public void setApplicationContext(ApplicationContext context) {
        if (this.initialized.compareAndSet(false, true)) {
            if (context.getBeanNamesForType(ConfigurationService.class).length > 0) {
                setConfigurationService(context.getBean(ConfigurationService.class));
            }
        }
    }

    /**
     * 执行限流判断：调用Lua脚本执行令牌桶逻辑。
     * @param routeId gateway路由ID
     * @param id 限流维度标识（来自KeyResolver，一般为客户端IP）
     * @return Mono<Response> allowed：是否放行；headers：限流响应头
     */
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
            return Flux.just(Arrays.asList(SCRIPT_ALLOWED_VALUE, UNKNOWN_TOKENS_LEFT));
        }).next().map(results -> {
            boolean allowed = results.get(SCRIPT_ALLOWED_INDEX) == SCRIPT_ALLOWED_VALUE;
            Long tokensLeft = results.get(SCRIPT_TOKENS_LEFT_INDEX);
            Response response = new Response(allowed, getHeaders(routeConfig, tokensLeft));
            if (log.isDebugEnabled()) {
                log.debug("response: " + response);
            }
            return response;
        });
    }

    /**
     * 组装Redis key，使用Hash‑Tag保证双key落在同一个slot，Lua脚本原子执行。
     * @param id 限流维度标识（IP等）
     * @param routeId gateway路由ID
     * @return tokens、timestamp两个key
     */
    private List<String> getKeys(String id, String routeId) {
        String prefix = RedisKeyConstants.RATE_KEY_PREFIX + ".{" + routeId + "." + id + "}.";
        return Arrays.asList(prefix + "tokens", prefix + "timestamp");
    }

    /**
     * 加载路由限流配置；优先取当前routeId配置，取不到回退到defaultFilters全局默认配置。
     * @param routeId 路由ID
     * @return 限流配置 replenishRate / burstCapacity / requestedTokens
     */
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

    /**
     * 构造限流响应头，返回给调用方。
     * @param config 限流配置
     * @param tokensLeft 剩余令牌数
     * @return header map
     */
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
