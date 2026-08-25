package com.agentdoc.agent.execution.runtime;

import com.agentdoc.agent.execution.model.TokenUsage;

/**
 * Agent任务整体执行最终结果
 * <p>
 * 一次Agent完整任务执行完毕后的返回数据，由{@link AgentExecutionRuntime#execute(…)}返回。
 * 汇总任务摘要以及全流程累计的Token消耗用量。
 * </p>
 * @param summary Agent执行完成后的总结输出文本
 * @param tokenUsage 整个Agent任务全轮次累加后的Token统计
 */
public record AgentRuntimeResult(
        String summary,
        TokenUsage tokenUsage) {
}
