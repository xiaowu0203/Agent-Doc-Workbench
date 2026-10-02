package com.agentdoc.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 恢复签发的部署身份；只通过环境变量或被忽略配置提供密钥。 */
@Data
@Component
@ConfigurationProperties(prefix = "agent-doc.task-recovery")
public class TaskRecoverySigningProperties {
    /** 当前专属机器密钥，空值禁用签发；至少 32 字节随机值。 */
    private String machineKey = "";
    /** 正常轮换的前一密钥；紧急撤销时直接清空。 */
    private String previousMachineKey = "";
    /** 正常轮换切换时间，前一密钥仅在之后 300 秒内接受。 */
    private Instant keyRotatedAt;

    @Override
    public String toString() { return "TaskRecoverySigningProperties[密钥已隐藏]"; }
}
