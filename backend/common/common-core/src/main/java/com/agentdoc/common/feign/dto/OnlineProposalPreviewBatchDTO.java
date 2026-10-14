package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * 同一冻结输入上的原始提案批量预览，避免逐条 RPC。
 * @param documentId 目标文档
 * @param baseVersion 冻结输入版本
 * @param baseContentSha256 冻结输入正文摘要
 * @param proposals 完整原始提案集合
 */
public record OnlineProposalPreviewBatchDTO(Long documentId, Long baseVersion, String baseContentSha256,
        List<OnlineProposalPreviewDTO> proposals) { }
