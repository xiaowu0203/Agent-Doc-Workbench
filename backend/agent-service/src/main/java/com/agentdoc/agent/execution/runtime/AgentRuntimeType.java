package com.agentdoc.agent.execution.runtime;

/**
 * AgentRuntime实现类型枚举
 * <p>
 * 配合配置 {@code agent-doc.agent.runtime.type} 使用，用来选择实例化哪一套Agent执行实现。
 * <ul>
 * <li>CUSTOM：使用项目自研完整AgentRuntime实现</li>
 * <li>SPRING_AI_ALIBABA：使用桥接适配器，委托Spring‑AI Alibaba原生Agent能力执行</li>
 * </ul>
 * </p>
 */
public enum AgentRuntimeType {
    /** 项目自研Agent Runtime实现 */
    CUSTOM,
    /** 桥接适配 Spring‑AI Alibaba 原生Agent */
    SPRING_AI_ALIBABA
}
