package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * 文档版本来源批量查询。
 *
 * @param spaceId 所属空间 ID
 * @param changeRequestIds 来源变更请求 ID 集合
 * @param taskIds 来源任务 ID 集合
 */
public record DocumentVersionSourceQueryDTO(
        Long spaceId,
        List<Long> changeRequestIds,
        List<Long> taskIds) {
}

