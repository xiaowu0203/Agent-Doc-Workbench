package com.agentdoc.auth.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** OIDC身份绑定的精确可信issuer；未配置时绑定与解析失败关闭。 */
@Data
@ConfigurationProperties(prefix = "workbench.auth.oidc")
public class IdentityBindingProperties {
    /** 已核验的唯一OIDC发行者，必须与令牌issuer逐字符一致。 */
    private String issuer = "";
    /** OIDC发现文档中的令牌introspection端点。 */
    private String introspectionUri = "";
    /** 在人类Access的aud中的受限机密客户端。 */
    private String introspectionClientId = "workbench-api";
    /** 从受管环境注入的客户端密钥。 */
    @ToString.Exclude
    private String introspectionClientSecret = "";
}
