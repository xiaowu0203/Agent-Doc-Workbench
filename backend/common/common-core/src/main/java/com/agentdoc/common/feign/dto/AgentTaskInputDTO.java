package com.agentdoc.common.feign.dto;

public record AgentTaskInputDTO(
        Long workbenchTaskId,
        Long agentId,
        Long spaceId,
        Long documentId,
        Long tokenBudget,
        String mcpServerUrl,
        String taskCapability) {
}
