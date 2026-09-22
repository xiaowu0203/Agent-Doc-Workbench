package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * task-service 请求 auth-service 签发任务能力 JWT 的内部契约。
 */
public record TaskCapabilityIssueDTO(
        Long taskId,
        Long agentId,
        Long spaceId,
        Long documentId,
        String executionMode,
        Long documentVersionSnapshot,
        String documentContentSha256,
        Integer inputSnapshotSchemaVersion,
        String inputSnapshotHash,
        List<String> actions) {
}
