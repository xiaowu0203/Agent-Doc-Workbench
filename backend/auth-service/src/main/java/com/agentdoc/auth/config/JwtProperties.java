package com.agentdoc.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置：密钥与有效期。
 */
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
        // RSA私钥
        String privateKey,
        // RSA公钥
        String publicKey,
        // AccessToken 有效期
        Duration accessTtl,
        // RefreshToken 有效期
        Duration refreshTtl,
        // JWT签发者标识
        String issuer) {
}
