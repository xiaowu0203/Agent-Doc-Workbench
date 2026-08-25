package com.agentdoc.agent.execution.runtime;

public class AgentExecutionCanceledException extends RuntimeException {

    public AgentExecutionCanceledException() {
        super("任务已取消");
    }
}
