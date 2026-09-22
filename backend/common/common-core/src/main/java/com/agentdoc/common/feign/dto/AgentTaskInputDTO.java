package com.agentdoc.common.feign.dto;

public record AgentTaskInputDTO(
        Long workbenchTaskId,
        Long agentId,
        Long spaceId,
        Long documentId,
        Long tokenBudget,
        String executionMode,
        Long documentVersionSnapshot,
        String documentContentSha256,
        Integer inputSnapshotSchemaVersion,
        String inputSnapshotHash,
        Long sourceTaskId,
        Long sourceExecutionId,
        Integer sourceExecutionSnapshotSchemaVersion,
        String sourceExecutionSnapshotHash,
        String mcpServerUrl,
        String taskCapability) {
}
