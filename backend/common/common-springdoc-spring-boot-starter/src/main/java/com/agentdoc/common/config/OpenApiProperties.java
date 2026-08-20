package com.agentdoc.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAPI 文档元信息配置（agent-doc.openapi.*）。
 * 各服务只需配置 title / description，安全方案与版本由 starter 统一提供。
 */
@ConfigurationProperties(prefix = "agent-doc.openapi")
public record OpenApiProperties(
        String title,
        String description,
        String version) {

    public OpenApiProperties {
        if (title == null || title.isBlank()) {
            title = "Agent-Doc-Workbench API";
        }
        if (description == null || description.isBlank()) {
            description = "Agent 活文档协作平台服务接口";
        }
        if (version == null || version.isBlank()) {
            version = "v0.1.0";
        }
    }
}
