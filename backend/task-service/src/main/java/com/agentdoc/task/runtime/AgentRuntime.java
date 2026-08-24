package com.agentdoc.task.runtime;

import com.agentdoc.task.pojo.entity.AgentEntity;

/**
 * 外部 Agent 执行抽象。Phase 3 保持单 Agent 串行，不包含编排能力。
 */
public interface AgentRuntime {

    AgentExecutionResult execute(AgentEntity agent, AgentExecutionContext context);
}
