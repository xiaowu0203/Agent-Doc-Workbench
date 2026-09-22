package com.agentdoc.common.feign.vo;

/**
 * 隔离执行产物追加结果。
 * <p>
 * ISOLATED模式下保存候选变更产物后的返回值，返回产物主键与校验哈希，
 * 用于回填至执行快照，参与后续A2A能力令牌的一致性校验。
 *
 * @param artifactId      新增产物唯一主键ID
 * @param artifactType    产物类型，如 CHANGE_PROPOSAL、DRAFT_CHANGES
 * @param payloadSha256   载荷内容SHA256摘要，防篡改校验依据
 */
public record ExecutionArtifactAppendVO(
        Long artifactId,
        String artifactType,
        String payloadSha256) {
}
