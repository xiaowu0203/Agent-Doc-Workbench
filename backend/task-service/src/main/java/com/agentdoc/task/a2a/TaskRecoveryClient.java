package com.agentdoc.task.a2a;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.TaskRecoveryRemoteVO;
import com.agentdoc.task.config.TaskRecoveryProperties;
import org.a2aproject.sdk.spec.Task;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** 专用恢复 HTTP 客户端；不设置 Authorization 或 X-Task-Capability。 */
@Component
public class TaskRecoveryClient {
    private final RestClient client;

    public TaskRecoveryClient(RestClient.Builder builder, TaskRecoveryProperties properties) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs())).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        client = builder.clone().requestFactory(factory).baseUrl(properties.getAgentUrl()).build();
    }

    public TaskRecoveryRemoteVO<Task> query(String a2aId, String capability, boolean cancel) {
        String path = "/api/agent/internal/a2a/tasks/{id}/" + (cancel ? "recovery-cancel" : "recovery");
        Result<TaskRecoveryRemoteVO<Task>> result = (cancel ? client.post() : client.get())
                .uri(path, a2aId).header(TaskRecoveryConstant.CAPABILITY_HEADER, capability).retrieve()
                .body(new ParameterizedTypeReference<>() { });
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            String reason = result != null && "RECOVERY_IDENTITY_MISMATCH".equals(result.message())
                    ? result.message() : "REMOTE_TASK_UNAVAILABLE";
            throw new BusinessException(ErrorCode.CONFLICT, reason);
        }
        return result.data();
    }
}
