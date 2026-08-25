package com.agentdoc.task.mcp;

public record McpChangeProposalResult(
        Long changeRequestId,
        String status) {
}
