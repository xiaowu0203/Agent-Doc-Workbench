package com.agentdoc.evaluation.enums;

/**
 * 可引用的最小证据来源类型。
 * <p>
 * 评估执行过程中可用于校验、审计的原始证据分类，用于标记评估结果引用的数据来源。
 * </p>
 */
public enum EvaluationEvidenceType {
    /** 任务实例 */
    TASK,
    /** Agent 执行记录 */
    AGENT_EXECUTION,
    /** 执行产出物/制品 */
    EXECUTION_ARTIFACT,
    /** Token 消耗流水账本 */
    TOKEN_LEDGER,
    /** 调用链路追踪日志 */
    TRACE,
    /** 变更请求 */
    CHANGE_REQUEST,
    /** 文档版本快照 */
    DOCUMENT_VERSION,
    /** 审计日志 */
    AUDIT_LOG
}
