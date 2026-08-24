package com.agentdoc.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务服务安全配置。
 */
@Data
@ConfigurationProperties(prefix = "agent-doc.security")
public class SecurityVerifyProperties {

    /**
     * auth-service 的 JWK Set 地址，业务服务从这里拉取并缓存 RSA 公钥。
     */
    private String jwksUrl;

    /**
     * 是否启用任务能力令牌请求过滤器，默认关闭。
     */
    private boolean taskCapabilityFilterEnabled;
}
