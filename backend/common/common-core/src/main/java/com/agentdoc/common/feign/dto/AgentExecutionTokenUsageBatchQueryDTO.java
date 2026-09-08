package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * 批量查询 Agent 执行 Token 用量的参数。
 *
 * @param taskIds 工作台任务 ID 集合
 */
public record AgentExecutionTokenUsageBatchQueryDTO(List<Long> taskIds) {
}
