package com.agentdoc.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务服务安全配置（模型 B：Spring Security Resource Server）。
 * <p>
 * 配置 {@code agent-doc.security.jwks-url}（非空）即启用 JWT 解码器（{@code businessJwtDecoder}），
 * 供 Security Resource Server 从 Authorization 自行解析 JWT 身份；未配置时降级为纯注解驱动
 * （{@code @RequireLogin} 拦截器兜底，无 Security 层解析）。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "agent-doc.security")
public class SecurityVerifyProperties {

    /**
     * auth-service 的 JWK Set 地址，业务服务从这里拉取并缓存 RSA 公钥解析 JWT；
     * 为空表示不启用 Security Resource Server 解析（默认）。
     */
    private String jwksUrl;
}
