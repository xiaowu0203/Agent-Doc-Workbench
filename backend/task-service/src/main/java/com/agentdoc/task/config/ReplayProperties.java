package com.agentdoc.task.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Replay 创建门禁；仅用于紧急停止新建，既有任务仍按持久化状态处理。 */
@Data
@ConfigurationProperties(prefix = "agent-doc.replay")
public class ReplayProperties {
    /** 是否允许创建新的 Replay Task。 */
    private boolean creationEnabled = true;
}
