package com.agentdoc.task.enums;

/**
 * 审计日志操作类型。
 */
public enum AuditAction {

    AGENT_CREATED,
    AGENT_UPDATED,
    AGENT_DELETED,
    TASK_CREATED,
    TASK_STARTED,
    TASK_COMPLETED,
    TASK_TERMINATED,
    TASK_BUDGET_TERMINATED,
    TASK_RETRY,
    TASK_FAILED
}
