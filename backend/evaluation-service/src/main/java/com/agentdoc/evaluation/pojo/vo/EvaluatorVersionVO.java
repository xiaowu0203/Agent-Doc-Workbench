package com.agentdoc.evaluation.pojo.vo;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import java.time.LocalDateTime;
public record EvaluatorVersionVO(Long id, Long evaluatorId, Long spaceId, Integer versionNo, String status,
        String evaluatorKey, Integer configSchemaVersion, String configJson, Integer resultSchemaVersion,
        String implementationVersion, String contentHash, LocalDateTime publishedAt, Long createdBy) {
    public static EvaluatorVersionVO from(EvaluatorVersionEntity value) {
        return new EvaluatorVersionVO(value.getId(), value.getEvaluatorId(), value.getSpaceId(), value.getVersionNo(),
                value.getStatus(), value.getEvaluatorKey(), value.getConfigSchemaVersion(), value.getConfigJson(),
                value.getResultSchemaVersion(), value.getImplementationVersion(), value.getContentHash(),
                value.getPublishedAt(), value.getCreatedBy());
    }
}
