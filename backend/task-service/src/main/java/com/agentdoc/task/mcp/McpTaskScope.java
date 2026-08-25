package com.agentdoc.task.mcp;

public record McpTaskScope(
        Long taskId,
        Long agentId,
        Long spaceId,
        Long documentId) {
}
