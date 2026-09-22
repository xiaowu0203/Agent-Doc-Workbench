package com.agentdoc.common.feign.dto;

import java.util.List;

/** Evaluation 对冻结文档版本执行候选结构化变更的只读预览请求。 */
public record EvaluationDocumentChangePreviewDTO(
        Long documentId,
        Long baseVersion,
        String baseContentSha256,
        List<ChangeItemDTO> changes) {
}
