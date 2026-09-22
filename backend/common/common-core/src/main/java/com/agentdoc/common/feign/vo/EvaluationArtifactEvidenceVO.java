package com.agentdoc.common.feign.vo;

/**
 * Evaluation 使用的不可变 Artifact 元数据，不包含 payload 正文。
 * <p>
 * 用于评估回放与指标核算，仅携带校验哈希与索引信息，不存储完整报文，减少传输与存储开销；
 * 可通过 payloadSha256 校验原始捕获产物未被篡改。
 *
 * @param id                产物主键ID
 * @param sequenceNo        产物递增序列号，保留隔离捕获时的调用顺序，保证快照可复现
 * @param artifactType      产物类型，例如 CHANGE_PROPOSAL、DRAFT_CHANGES
 * @param schemaVersion     payload 结构版本，用于序列化解析兼容
 * @param payloadSha256     产物载荷SHA256哈希，防篡改校验依据
 * @param sourceToolCallId 关联的工具调用记录ID，可为null，用于链路关联
 */
public record EvaluationArtifactEvidenceVO(
        Long id,
        Integer sequenceNo,
        String artifactType,
        Integer schemaVersion,
        String payloadSha256,
        Long sourceToolCallId) {
}
