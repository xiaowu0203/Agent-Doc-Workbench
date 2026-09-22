package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * Evaluation 对冻结文档版本执行候选结构化变更的只读预览请求。
 * <p>
 * 仅在评估阶段使用，基于指定基线文档与变更集合，计算预览后的文档结果，
 * 不会写入真实文档库，无持久化副作用。
 *
 * @param documentId          目标文档ID
 * @param baseVersion         基线文档冻结版本号，变更基于该版本计算
 * @param baseContentSha256   基线文档内容SHA256哈希，用于校验基线文档未被篡改
 * @param changes             候选结构化变更项列表，将要应用到基线版本的修改集合
 */
public record EvaluationDocumentChangePreviewDTO(
        Long documentId,
        Long baseVersion,
        String baseContentSha256,
        List<ChangeItemDTO> changes) {
}
