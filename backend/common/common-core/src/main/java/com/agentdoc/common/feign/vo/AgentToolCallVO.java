package com.agentdoc.common.feign.vo;


/**
 * 脱敏工具调用及其所属工作台任务。
 *
 * @param workbenchTaskId 工作台任务 ID
 * @param toolCall 工具调用明细
 */
public record AgentToolCallVO(
       Long workbenchTaskId,
       AgentExecutionAuditVO.ToolCall toolCall) {
}
