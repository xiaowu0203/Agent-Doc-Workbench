package com.agentdoc.task.pojo.vo;

import com.agentdoc.common.feign.vo.AgentExecutionAuditVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 任务工具调用明细批量查询结果。
 */
@Schema(description = "任务工具调用明细")
public record TaskToolCallVO(
        @Schema(description = "工作台任务 ID") Long taskId,
        @Schema(description = "脱敏工具调用") AgentExecutionAuditVO.ToolCall toolCall) {
}
