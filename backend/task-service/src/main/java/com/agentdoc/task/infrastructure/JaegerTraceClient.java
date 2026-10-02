package com.agentdoc.task.infrastructure;

import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.task.config.TaskTraceProperties;
import com.agentdoc.task.constant.TaskTraceConstant;
import com.agentdoc.task.enums.TaskTraceAvailability;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 内部只读 Jaeger 适配；不转发用户身份、不跟随重定向、不返回原始响应。 */
@Component
public class JaegerTraceClient {
    private final TaskTraceProperties properties;
    private final RestClient client;

    public JaegerTraceClient(TaskTraceProperties properties) {
        this.properties = properties;
        var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(TaskTraceConstant.CONNECT_TIMEOUT_MS)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(TaskTraceConstant.READ_TIMEOUT_MS));
        client = RestClient.builder().requestFactory(factory).build();
    }

    public Fetch fetch(String traceId) {
        if (properties.getQueryUrl() == null || properties.getQueryUrl().isBlank()) {
            return new Fetch(TaskTraceAvailability.NOT_CONFIGURED, null);
        }
        if (traceId == null || !traceId.matches("[0-9a-f]{32}") || traceId.matches("0{32}")) {
            return new Fetch(TaskTraceAvailability.INVALID_TRACE_ID, null);
        }
        String root = properties.getQueryUrl().replaceAll("/+$", "");
        try {
            return client.get().uri(root + "/api/traces/" + traceId).exchange((request, response) -> {
                if (response.getStatusCode().value() == 404) {
                    return new Fetch(TaskTraceAvailability.NOT_FOUND_OR_NOT_SAMPLED, null);
                }
                if (!response.getStatusCode().is2xxSuccessful()) {
                    return new Fetch(TaskTraceAvailability.BACKEND_UNAVAILABLE, null);
                }
                byte[] body = response.getBody().readNBytes(TaskTraceConstant.MAX_PAYLOAD_BYTES + 1);
                if (body.length > TaskTraceConstant.MAX_PAYLOAD_BYTES) {
                    return new Fetch(TaskTraceAvailability.PAYLOAD_TOO_LARGE, null);
                }
                JsonNode payload = JsonUtils.parseStrict(new String(body, StandardCharsets.UTF_8), JsonNode.class);
                return new Fetch(payload == null ? TaskTraceAvailability.PAYLOAD_INVALID
                        : TaskTraceAvailability.AVAILABLE, payload);
            });
        } catch (RuntimeException unavailable) {
            return new Fetch(TaskTraceAvailability.BACKEND_UNAVAILABLE, null);
        }
    }

    /** 原始数据仅在适配层与投影转换器之间使用，不是公开 API 类型。 */
    public record Fetch(TaskTraceAvailability code, JsonNode payload) { }
}
