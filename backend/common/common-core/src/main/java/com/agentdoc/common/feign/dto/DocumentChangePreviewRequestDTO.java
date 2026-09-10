package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * 文档变更预览请求。
 *
 * @param documentId 文档 ID
 * @param baseVersion 变更基准版本
 * @param changes 原始结构化变更
 */
public record DocumentChangePreviewRequestDTO(
        Long documentId,
        Long baseVersion,
        List<ChangeItemDTO> changes) {
}
