package com.agentdoc.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** P0 内部接收方配置；空/过短凭据拒绝服务，不生成默认机器密钥。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "agent-doc.task-internal-auth")
public class TaskInternalAuthProperties {

    /** 独立 Task 服务凭据，至少32字节，仅从环境变量/受管Secret提供。 */
    private String secret = "";
    /** 非TLS且非本机回环连接时，是否已由部署方确认受信加密内部网络。 */
    private boolean trustedEncryptedNetwork;
}
