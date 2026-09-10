package com.agentdoc.task.pojo.vo;

import com.agentdoc.common.feign.vo.AgentToolSourceCountVO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Token 用量看板聚合响应。
 */
@Schema(description = "Token 用量看板")
public record TokenUsageDashboardVO(
        @Schema(description = "统计开始日期（含）") LocalDate startDate,
        @Schema(description = "统计结束日期（含）") LocalDate endDate,
        @Schema(description = "数据计算时间") LocalDateTime calculatedAt,
        @Schema(description = "统计业务时区") String timeZone,
        @Schema(description = "当前周期摘要") TokenUsageSummaryVO summary,
        @Schema(description = "等长上一周期摘要") TokenUsageSummaryVO previousSummary,
        @Schema(description = "连续自然日趋势") List<TokenUsageDailyVO> trend,
        @Schema(description = "工具来源分布") List<AgentToolSourceCountVO> toolSources,
        @Schema(description = "本月已使用 Token") long monthlyUsedTokens,
        @Schema(description = "空间月度 Token 预算，未设置时为 null") Long monthlyTokenBudget) {
}

