package com.agentdoc.common.feign.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Task内部调用配置；只作用于明确的签发/执行档案契约。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "agent-doc.task-internal-auth")
public class TaskInternalCallProperties {

    /** 独立机器凭据，至少32字节，不复用恢复/模型/签名密钥。 */
    private String secret = "";
    /** 签发服务直连根地址，不经公共Gateway。 */
    private String authUrl = "http://localhost:8081";
    /** 执行档案服务直连根地址，不经公共Gateway。 */
    private String agentUrl = "http://localhost:8084";
    /** 非TLS/回环地址是否已由部署方确认受信加密内部网络。 */
    private boolean trustedEncryptedNetwork;
}
