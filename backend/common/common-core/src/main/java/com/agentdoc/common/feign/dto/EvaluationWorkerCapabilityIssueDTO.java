package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * task-service 请求 auth-service 签发 Evaluation Worker 窄权限令牌的内部契约。
 */
public record EvaluationWorkerCapabilityIssueDTO(
        Long runId,
        Long spaceId,
        String taskIdsHash,
        Long ttlSeconds,
        List<String> actions) {
}
