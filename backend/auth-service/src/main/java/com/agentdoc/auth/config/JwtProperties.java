package com.agentdoc.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 既有Capability签名配置；人类身份使用独立auth.identity配置。
 */
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
        // RSA私钥
        String privateKey,
        // RSA公钥
        String publicKey,
        // JWT签发者标识
        String issuer) {
}
