package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.enums.TaskRecoveryReason;
import io.swagger.v3.oas.annotations.media.Schema;

/** 不含证明、JWT、正文或远端 payload 的恢复审计详情。 */
public record TaskRecoveryEventVO(
        @Schema(description = "恢复关联 UUID") String recoveryId,
        @Schema(description = "触发方式 AUTO/MANUAL") String trigger,
        @Schema(description = "固定协调服务身份") String service,
        @Schema(description = "真实人工触发用户 ID，自动恢复为空") Long userId,
        @Schema(description = "已关联的 A2A Task ID") String a2aTaskId,
        @Schema(description = "动作 QUERY/CANCEL/FINALIZE/WRITEBACK 或恢复结果") String action,
        @Schema(description = "稳定诊断原因") TaskRecoveryReason reason,
        @Schema(description = "本轮开始时间，ISO-8601") String startedAt,
        @Schema(description = "本轮结束时间，ISO-8601；开始事件为空") String finishedAt) { }
