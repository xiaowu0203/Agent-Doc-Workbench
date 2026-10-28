package com.agentdoc.task.pojo.vo;

import com.agentdoc.common.feign.vo.AgentExecutionAuditVO;
import io.swagger.v3.oas.annotations.media.Schema;

/** 工具调用分页及本页任务概要；来源任务缺失时保留调用身份。 */
public record TaskToolCallSearchVO(
        @Schema(description = "工作台任务ID") Long taskId,
        @Schema(description = "当前空间任务概要，任务缺失时为空") TaskListItemVO task,
        @Schema(description = "脱敏工具调用") AgentExecutionAuditVO.ToolCall toolCall) { }
