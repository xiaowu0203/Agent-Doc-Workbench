package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.enums.TaskRecoveryReason;
import io.swagger.v3.oas.annotations.media.Schema;

/** 从 Task 与追加审计派生，不持久化第二套恢复状态。 */
public record TaskRecoveryStatusVO(
        @Schema(description = "Task ID") Long taskId,
        @Schema(description = "当前 Task 业务状态码") Integer taskStatus,
        @Schema(description = "Task 协调端开关与机器密钥是否配置，不代表接收端健康") boolean configured,
        @Schema(description = "本地仍活动且原凭证已过期") boolean recoveryRequired,
        @Schema(description = "当前诊断原因") TaskRecoveryReason reason,
        @Schema(description = "最新脱敏恢复事件，没有事件时为空") TaskRecoveryEventVO latestEvent) { }
