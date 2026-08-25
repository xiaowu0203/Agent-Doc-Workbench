package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * 文档合并请求。
 * @param documentId    目标文档 ID
 * @param baseVersion   基线版本号（合并时校验，防止并发覆盖，不匹配则合并失败）
 * @param changes       结构化变更列表，按序应用
 * @param changeSummary 变更摘要（写入版本快照），可空
 */
public record MergeRequestDTO(Long documentId, Long baseVersion, List<ChangeItemDTO> changes,
                              String changeSummary) {
}
