package com.agentdoc.task.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 空间当日 Token 消耗卡片。
 */
@Schema(description = "空间当日 Token 消耗")
public record TokenUsageTodayVO(
        @Schema(description = "空间 ID") Long spaceId,
        @Schema(description = "输入 Token 数") Long inputTokens,
        @Schema(description = "输出 Token 数") Long outputTokens,
        @Schema(description = "输入与输出 Token 总数") Long tokens,
        @Schema(description = "预估费用，人民币") BigDecimal estimatedCost) {

    public static TokenUsageTodayVO of(Long spaceId, Long inputTokens, Long outputTokens,
                                       BigDecimal estimatedCost) {
        long normalizedInput = inputTokens == null ? 0 : inputTokens;
        long normalizedOutput = outputTokens == null ? 0 : outputTokens;
        return new TokenUsageTodayVO(
                spaceId, normalizedInput, normalizedOutput, normalizedInput + normalizedOutput, estimatedCost);
    }
}
