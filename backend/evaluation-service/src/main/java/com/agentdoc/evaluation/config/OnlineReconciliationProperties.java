package com.agentdoc.evaluation.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 线上部署开关默认关闭；扫描间隔 agent-doc.online.reconcile-delay-ms 默认 30000 毫秒。 */
@Data
@ConfigurationProperties(prefix = "agent-doc.online")
public class OnlineReconciliationProperties {
    /** 部署完成授权与对账依赖后才能启用；不绕过公共启动预检。 */
    private boolean enabled;
}
