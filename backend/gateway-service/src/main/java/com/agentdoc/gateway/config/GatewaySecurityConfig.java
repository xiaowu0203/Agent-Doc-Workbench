package com.agentdoc.gateway.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 网关安全相关Bean配置类，Spring Cloud Gateway(WebFlux)。
 * <p>包含能力：
 * <ul>
 * <li>JWT解码器：远程拉取Auth服务JWKS公钥集合，用于网关层JWT验签；网关只持有公钥，不持有私钥；</li>
 * <li>限流KeyResolver：两套key解析器，全局IP限流、登录接口独立IP限流；</li>
 * <li>自定义Redis令牌桶限流器：修改Redis key前缀，替换框架默认硬编码前缀，业务Redis key隔离。</li>
 * </ul>
 */
@Configuration
public class GatewaySecurityConfig {

    /**
     * JWT解码器，基于Auth服务提供的 /oauth2/jwks JWK集合URI构建。
     * <p>底层为 {@code RemoteJWKSet}：首次验签时远程拉取RSA公钥集合，本地缓存；
     * 公钥轮换时会自动重新拉取；网关仅做验签，不持有JWT私钥。
     * @param properties 网关鉴权配置，读取jwk‑url地址
     * @return JwtDecoder JWT解析验签实例
     */
    @Bean
    public JwtDecoder jwtDecoder(GatewayAuthProperties properties) {
        return NimbusJwtDecoder.withJwkSetUri(properties.getJwkUrl()).build();
    }

    /**
     * 全局限流KeyResolver，@Primary作为默认全局Key解析器。
     * <p>限流维度：客户端真实IP；Redis key前缀由自定义 {@link ProjectRedisRateLimiter}统一处理。
     * @return KeyResolver 响应式key解析器
     */
    @Bean
    @Primary
    public KeyResolver clientIpKeyResolver() {
        return exchange -> {
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            String ip = remote == null || remote.getAddress() == null
                    ? "unknown"
                    : remote.getAddress().getHostAddress();
            return Mono.just(ip);
        };
    }

    /**
     * 登录接口专用限流KeyResolver，仅供login‑rate‑limit路由过滤器引用。
     * <p>key增加 login: 前缀，与全局限流key空间隔离，实现两套令牌桶叠加校验；
     * 同样按客户端IP做限流维度，防止同一个IP暴力刷登录接口。
     * @return KeyResolver 登录限流key解析Bean，bean名称 loginKeyResolver
     */
    @Bean
    public KeyResolver loginKeyResolver() {
        return exchange -> {
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            String ip = remote == null || remote.getAddress() == null
                    ? "unknown"
                    : remote.getAddress().getHostAddress();
            return Mono.just("login:" + ip);
        };
    }

    /**
     * 加载令牌桶Lua脚本，脚本文件位于 classpath:scripts/request_rate_limiter.lua。
     * <p>脚本逻辑与Spring Cloud Gateway原生RedisRateLimiter逻辑保持一致。
     * @return RedisScript Lua脚本实例
     */
    @Bean
    public RedisScript<List> projectRateLimiterScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/request_rate_limiter.lua"));
        script.setResultType(List.class);
        return script;
    }

    /**
     * 自定义Redis令牌桶限流器，@Primary覆盖Gateway原生RedisRateLimiter。
     * <p>修改Redis存储key前缀为 agent‑doc‑workbench:rate，替换框架默认硬编码的 request_rate_limiter，
     * 实现多业务环境key隔离；Gateway的RequestRateLimiter过滤器会自动使用此实现。
     * @param redisTemplate Reactive响应式Redis模板（WebFlux必须使用ReactiveStringRedisTemplate）
     * @param script 令牌桶Lua脚本
     * @return ProjectRedisRateLimiter 自定义限流器实例
     */
    @Bean
    @Primary
    public ProjectRedisRateLimiter projectRedisRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            @Qualifier("projectRateLimiterScript") RedisScript<List> script) {
        return new ProjectRedisRateLimiter(redisTemplate, script);
    }
}
