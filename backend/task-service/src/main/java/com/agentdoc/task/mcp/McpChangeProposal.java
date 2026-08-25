package com.agentdoc.task.mcp;

import com.agentdoc.common.feign.dto.ChangeItemDTO;

import java.util.List;

public record McpChangeProposal(
        Long baseVersion,
        List<ChangeItemDTO> changes) {
}
