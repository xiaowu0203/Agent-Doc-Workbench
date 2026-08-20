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
 * 网关安全相关 Bean：JWT 公钥解码器（JWK Set 拉取+缓存）、限流 KeyResolver 与自定义限流器。
 */
@Configuration
public class GatewaySecurityConfig {

    /**
     * 基于 auth-service 的 /oauth2/jwks 构建解码器。
     * NimbusJwtDecoder.withJwkSetUri 内部即 RemoteJWKSet：首次访问拉取公钥并缓存，
     * 网关不持有私钥，仅用公钥验签。
     */
    @Bean
    public JwtDecoder jwtDecoder(GatewayAuthProperties properties) {
        return NimbusJwtDecoder.withJwkSetUri(properties.getJwkUrl()).build();
    }

    /**
     * 全局限流 Key：按客户端 IP 区分（工程名前缀由 {@link ProjectRedisRateLimiter} 统一承担）。
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
     * 登录限流专用 Key（仅 login-rate-limit 路由使用）：独立 id 确保与全局限流「两桶叠加」互不干扰。
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
     * 令牌桶 Lua 脚本（与框架 RedisRateLimiter 一致，自包含于本项目资源）。
     */
    @Bean
    public RedisScript<List> projectRateLimiterScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/request_rate_limiter.lua"));
        script.setResultType(List.class);
        return script;
    }

    /**
     * 自定义限流器：键前缀 agent-doc-workbench:rate（替代框架硬编码 request_rate_limiter）。
     * @Primary 确保 RequestRateLimiterGatewayFilterFactory 注入本实现（框架默认 RedisRateLimiter 闲置）。
     */
    @Bean
    @Primary
    public ProjectRedisRateLimiter projectRedisRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            @Qualifier("projectRateLimiterScript") RedisScript<List> script) {
        return new ProjectRedisRateLimiter(redisTemplate, script);
    }
}
