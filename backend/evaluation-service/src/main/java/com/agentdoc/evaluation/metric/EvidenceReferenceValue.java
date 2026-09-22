package com.agentdoc.evaluation.metric;

import com.agentdoc.evaluation.enums.EvaluationEvidenceType;

/**
 * Evaluator 输出的最小证据引用，不包含正文或 Artifact payload。
 * <p>
 * 轻量证据引用记录，仅存储证据元信息与定位信息；
 * 原始证据正文/大文件载荷不放在此结构，通过 locator 去外部存储读取。
 * </p>
 * @param referenceKey 证据唯一引用键，用于关联评估结果
 * @param evidenceType 证据类型枚举
 * @param businessId 业务侧资源ID
 * @param contentHash 证据内容哈希，用于校验内容完整性
 * @param summary 证据简短摘要，便于前端展示
 * @param locatorJson 证据定位信息JSON，用于拉取原始证据数据
 */
public record EvidenceReferenceValue(
        String referenceKey,
        EvaluationEvidenceType evidenceType,
        String businessId,
        String contentHash,
        String summary,
        String locatorJson) {
}
