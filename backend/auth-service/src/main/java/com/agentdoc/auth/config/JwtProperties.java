package com.agentdoc.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置：密钥与有效期。
 */
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        Duration accessTtl,
        Duration refreshTtl,
        String issuer) {
}
