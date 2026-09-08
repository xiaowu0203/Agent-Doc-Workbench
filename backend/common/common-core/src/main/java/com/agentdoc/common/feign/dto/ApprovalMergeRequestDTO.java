package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * 审批合并请求。
 *
 * @param changeRequestId 变更请求 ID，同时作为文档版本幂等键
 * @param documentId 目标文档 ID
 * @param baseVersion 审批基准版本
 * @param changes 原始结构化变更
 * @param resolvedContent 部分接受或修改后接受形成的最终 Markdown；整单接受时为空
 * @param changeSummary 版本变更摘要
 */
public record ApprovalMergeRequestDTO(
        Long changeRequestId,
        Long documentId,
        Long baseVersion,
        List<ChangeItemDTO> changes,
        String resolvedContent,
        String changeSummary) {
}
