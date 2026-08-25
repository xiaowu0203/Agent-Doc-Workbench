package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.pojo.entity.TokenUsageEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 空间历史日 Token 消耗。
 */
@Schema(description = "空间历史日 Token 消耗")
public record TokenUsageTrendVO(
        @Schema(description = "统计日期") LocalDate usageDate,
        @Schema(description = "Token 数量") Long tokens) {

    public static TokenUsageTrendVO from(TokenUsageEntity entity) {
        return new TokenUsageTrendVO(entity.getUsageDate(), entity.getTokens());
    }
}
