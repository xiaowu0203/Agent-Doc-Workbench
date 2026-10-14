package com.agentdoc.common.feign.vo;

/**
 * 原始变更证据的只读校验事实；不携带原始 changes 或正文。
 * @param evidenceId 原始提案或文档版本 ID
 * @param outputType CHANGE_REQUEST 或 DRAFT_DOCUMENT
 * @param baseVersion 原始变更基线
 * @param outputVersion 草稿收尾版本，提案为空
 * @param payloadHash 原始变更载荷或草稿正文摘要
 * @param contentHash 原始提案应用后的正文摘要，结构无效时为空
 * @param structureValid 结构与冻结基线是否有效
 * @param capturedAt 原始捕获时间
 * @param identityHash 原始来源与校验事实的身份摘要
 */
public record OnlineOriginalChangeItemVO(String evidenceId, String outputType, Long baseVersion,
        Long outputVersion, String payloadHash, String contentHash, boolean structureValid,
        String capturedAt, String identityHash) { }
