package com.agentdoc.common.feign.dto;

/**
 * 查询指定文档可执行 Agent 的参数。
 *
 * @param spaceId 空间 ID
 * @param documentId 目标文档 ID
 */
public record AgentTaskOptionQueryDTO(Long spaceId, Long documentId) {
}
