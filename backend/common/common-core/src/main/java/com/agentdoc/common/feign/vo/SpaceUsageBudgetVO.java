package com.agentdoc.common.feign.vo;

/**
 * 空间用量页月度 Token 预算。
 *
 * @param spaceId 空间 ID
 * @param monthlyTokenBudget 月度 Token 预算，null 表示未设置
 */
public record SpaceUsageBudgetVO(Long spaceId, Long monthlyTokenBudget) {
}
