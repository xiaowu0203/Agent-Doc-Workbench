package com.agentdoc.task.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 空间本月 Token 用量与预算。
 *
 * @param usedTokens 本月已使用 Token 数
 * @param tokenBudget 空间 Token 预算，null 表示未设置预算
 */
@Schema(description = "空间本月 Token 用量与预算")
public record MonthlyTokenBudgetVO(
        @Schema(description = "本月已使用 Token 数") long usedTokens,
        @Schema(description = "空间 Token 预算，未设置时为 null") Long tokenBudget) {
}
