package com.agentdoc.common.feign.vo;

/**
 * 任务创建时读取的空间 Token 预算。
 */
public record SpaceBudgetVO(Long spaceId, Long tokenBudget) {
}
