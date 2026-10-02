package com.agentdoc.task.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

/** Jaeger 只读查询部署地址；空值禁用，不接受来自请求的 URL。 */
@Data
@Component
@ConfigurationProperties(prefix = "agent-doc.task-trace")
public class TaskTraceProperties {
    /** Jaeger 内部查询根地址，默认空；不要在地址中嵌入凭证。 */
    private String queryUrl = "";

    @PostConstruct
    public void validate() {
        if (queryUrl == null || queryUrl.isBlank()) { return; }
        URI endpoint = URI.create(queryUrl);
        if (!Set.of("http", "https").contains(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("Jaeger 查询地址必须为无凭证、查询或 fragment 的 HTTP(S) 根地址");
        }
    }
}
