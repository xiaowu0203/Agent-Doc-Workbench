package com.agentdoc.common.config;

import com.agentdoc.common.enums.AuthenticationMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 模式统一绑定；未知枚举拒绝启动，不默默回退local。 */
@Data
@ConfigurationProperties(prefix = "workbench.auth")
public class WorkbenchAuthProperties {
    /** 新装默认local；交付生产模板必须显式设置模式。 */
    private AuthenticationMode mode = AuthenticationMode.LOCAL;
}
