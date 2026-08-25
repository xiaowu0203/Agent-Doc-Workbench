package com.agentdoc.agent.enums;

/**
 * Agent执行枚举
 */
public enum AgentExecutionStatus {
    // 已提交
    SUBMITTED,
    // 执行中
    WORKING,
    // 等待输入
    INPUT_REQUIRED,
    // 授权中
    AUTH_REQUIRED,
    // 已完成
    COMPLETED,
    // 执行失败
    FAILED,
    // 已取消
    CANCELED
}
