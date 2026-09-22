package com.agentdoc.task.enums;

/**
 * 隔离执行允许持久化的候选产物类型枚举。
 * <p>限定回放/隔离执行场景下可以落地保存的输出产物，其它类型产物不允许持久化。</p>
 */
public enum ExecutionArtifactType {
    /** 修改提案 */
    CHANGE_PROPOSAL,
    /** 草稿变更内容 */
    DRAFT_CHANGES,
    /** 结果摘要 */
    RESULT_SUMMARY
}