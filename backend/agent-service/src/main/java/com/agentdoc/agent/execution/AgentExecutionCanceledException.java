package com.agentdoc.agent.execution;

public class AgentExecutionCanceledException extends RuntimeException {

    public AgentExecutionCanceledException() {
        super("任务已取消");
    }
}
