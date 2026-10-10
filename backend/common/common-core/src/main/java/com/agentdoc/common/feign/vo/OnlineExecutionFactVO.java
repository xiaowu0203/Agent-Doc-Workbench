package com.agentdoc.common.feign.vo;

/** Agent 只读事实与恢复原 Task 所需的非秘密远端身份。
 * @param fact 执行权威事实
 * @param a2aTaskId 原 A2A 身份，禁止替换另一执行
 * @param a2aContextId 原 A2A 上下文
 * @param tokenUsage 原执行冻结价格与用量；不以当前价格补历史 */
public record OnlineExecutionFactVO(OnlineTaskFactVO fact, String a2aTaskId, String a2aContextId, AgentExecutionTokenUsageVO tokenUsage) { }
