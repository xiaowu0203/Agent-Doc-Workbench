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
    /** 审批前捕获的 Agent 原始最终文本 */
    ORIGINAL_TEXT,
    /** LIVE 审批前原始提案或草稿收尾版本。 */
    ORIGINAL_CHANGE,
    /** 单个 LIVE Task 的完整原始变更集合；评分按任务而非产物数计算。 */
    ORIGINAL_CHANGE_SET,
    /** 审计日志 */
    AUDIT_LOG
}
