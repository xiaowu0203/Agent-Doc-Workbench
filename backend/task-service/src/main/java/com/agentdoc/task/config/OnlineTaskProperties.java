package com.agentdoc.task.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 线上流量门禁默认关闭；开启后路由不可用必须拒绝创建。 */
@Data
@Component
@ConfigurationProperties(prefix = "agent-doc.online")
public class OnlineTaskProperties {
    /** 是否对新 ORIGINAL 创建执行权威线上路由。 */
    private boolean enabled;
}
