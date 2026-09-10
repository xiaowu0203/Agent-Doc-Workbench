package com.agentdoc.task.pojo.param;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.task.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static com.agentdoc.task.constant.TaskConstant.MAX_TREND_DAYS;

/**
 * Token 用量看板查询条件，日期范围首尾均包含。
 */
@Schema(description = "Token 用量看板查询条件")
public record TokenUsageDashboardParam(
        @NotNull @Schema(description = "空间 ID") Long spaceId,
        @NotNull @Schema(description = "开始日期（含）") LocalDate startDate,
        @NotNull @Schema(description = "结束日期（含）") LocalDate endDate,
        @Schema(description = "Agent ID") Long agentId,
        @Schema(description = "模型 ID") Long modelId,
        @Schema(description = "任务状态") TaskStatus status) {

    public void validate() {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用量查询日期范围不合法");
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > MAX_TREND_DAYS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "用量查询日期范围不能超过 " + MAX_TREND_DAYS + " 天");
        }
    }
}

