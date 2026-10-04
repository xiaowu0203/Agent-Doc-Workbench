package com.agentdoc.common.feign.vo;

/**
 * 恢复内部协议投影；绝不返回浏览器。
 * @param remoteTask 已有 A2A Task（泛型避免 common-core 依赖 SDK）
 * @param tokenUsage 同一执行的权威 Token/计价快照
 * @param <T> A2A SDK Task 或协议 JSON 表示
 */
public record TaskRecoveryRemoteVO<T>(T remoteTask, AgentExecutionTokenUsageVO tokenUsage) { }
