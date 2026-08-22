package com.agentdoc.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 网关鉴权配置：JWKS 地址与免鉴权白名单。
 */
@ConfigurationProperties(prefix = "auth.gateway")
public class GatewayAuthProperties {

    /** auth-service 的 JWK Set 地址，网关从这里拉取并缓存 RSA 公钥 */
    private String jwkUrl = "http://localhost:8081/oauth2/jwks";

    /** 免鉴权路径白名单（Ant 风格，/** 通配） */
    private List<String> whitelist = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/ping",
            "/api/document/ping",
            "/api/task/ping",
            "/oauth2/jwks",
            "/actuator/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api/*/v3/api-docs",
            "/api/*/v3/api-docs/**",
            "/favicon.ico"
    );

    public String getJwkUrl() {
        return jwkUrl;
    }

    public void setJwkUrl(String jwkUrl) {
        this.jwkUrl = jwkUrl;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }
}
