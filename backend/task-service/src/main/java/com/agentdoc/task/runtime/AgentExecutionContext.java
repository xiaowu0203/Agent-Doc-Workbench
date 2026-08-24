package com.agentdoc.task.runtime;

/**
 * Agent Runtime 执行上下文。
 */
public record AgentExecutionContext(Long taskId, Long agentId, Long documentId,
                                    String instruction, String documentFragment,
                                    long fragmentStart, long documentLength) {
}
