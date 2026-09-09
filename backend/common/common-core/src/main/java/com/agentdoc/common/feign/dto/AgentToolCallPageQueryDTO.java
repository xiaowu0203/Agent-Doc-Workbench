package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * 分页查询工作台任务对应的 Agent 工具调用。
 *
 * @param spaceId 空间 ID
 * @param taskIds 工作台任务 ID 集合
 * @param pageNum 页码
 * @param pageSize 每页条数
 */
public record AgentToolCallPageQueryDTO(
        Long spaceId,
        List<Long> taskIds,
        Integer pageNum,
        Integer pageSize) {
}
