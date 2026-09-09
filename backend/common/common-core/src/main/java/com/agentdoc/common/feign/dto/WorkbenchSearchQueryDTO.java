package com.agentdoc.common.feign.dto;

/**
 * 工作台全局搜索查询参数。
 *
 * @param spaceId 空间 ID
 * @param keyword 搜索关键词
 * @param limitPerType 每类最多返回条数
 */
public record WorkbenchSearchQueryDTO(Long spaceId, String keyword, Integer limitPerType) {
}
