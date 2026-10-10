package com.agentdoc.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

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

    /** 是否接收线上保护窄凭证，默认关闭；不控制实验启动就绪。 */
    private boolean onlineCapabilityEnabled;
    /** 线上保护 JWT 的 issuer，必须与 auth-service 一致。 */
    private String onlineCapabilityIssuer = "agent-doc-workbench";

    /** 恢复 JWT 的固定 issuer，必须与 auth-service 一致。 */
    private String taskRecoveryIssuer = "agent-doc-workbench";

    /** 是否接收恢复窄凭证，默认关闭；紧急隔离时关闭。 */
    private boolean taskRecoveryEnabled;
    /** 恢复直连 HTTP 仅在运维确认等价加密的内部网络中放行；默认仅允许 TLS/loopback。 */
    private boolean taskRecoveryTrustedEncryptedNetwork;

    /**
     * 是否启用任务能力令牌请求过滤器，默认关闭。
     */
    private boolean taskCapabilityFilterEnabled;

    /**
     * 允许从 Authorization: Bearer 头解析任务能力令牌的请求路径。
     */
    private List<String> capabilityAuthEndpoints = List.of(
            "/mcp",
            "/mcp/**",
            "/a2a",
            "/a2a/**"
    );
}
