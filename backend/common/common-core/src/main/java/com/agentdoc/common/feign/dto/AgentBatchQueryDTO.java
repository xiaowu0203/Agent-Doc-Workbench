package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * Agent 最小引用信息批量查询参数。
 *
 * @param agentIds Agent ID 集合
 */
public record AgentBatchQueryDTO(List<Long> agentIds) {
}
