package com.agentdoc.agent.enums;

/**
 * 模型调用审计状态。
 */
public enum ModelCallAuditStatus {
    /** 调用已开始。 */
    STARTED,
    /** 调用成功结束。 */
    SUCCEEDED,
    /** 调用失败或审计结束信息缺失。 */
    FAILED
}
