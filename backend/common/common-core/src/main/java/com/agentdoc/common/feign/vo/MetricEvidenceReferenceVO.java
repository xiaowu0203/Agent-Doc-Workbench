package com.agentdoc.common.feign.vo;

/**
 * 标准 Metric 关联的最小证据引用，不包含证据正文。
 *
 * @param id 证据引用 ID
 * @param evidenceType 证据类型
 * @param businessId 证据业务 ID
 * @param contentHash 证据内容哈希
 * @param summary 脱敏摘要
 * @param locatorJson 结构化定位信息
 */
public record MetricEvidenceReferenceVO(
        Long id,
        String evidenceType,
        String businessId,
        String contentHash,
        String summary,
        String locatorJson) {
}
