package com.agentdoc.agent.execution.runtime;

/**
 * Agent执行迭代超限异常
 * <p>
 * Agent多轮工具循环保护异常，防止出现无限tool‑call死循环。
 * 当执行轮数达到配置的最大迭代阈值时抛出，终止Agent任务。
 * 属于业务状态异常，需要记录错误并结束任务，不可继续重试。
 * </p>
 */
public class AgentExecutionLimitExceededException extends IllegalStateException {
    /** 配置允许的最大Agent迭代轮数 */
    private final int maxIterations;

    public AgentExecutionLimitExceededException(int maxIterations) {
        super("Agent 工具调用超过最大迭代次数: " + maxIterations);
        this.maxIterations = maxIterations;
    }

    public int getMaxIterations() {
        return maxIterations;
    }
}
