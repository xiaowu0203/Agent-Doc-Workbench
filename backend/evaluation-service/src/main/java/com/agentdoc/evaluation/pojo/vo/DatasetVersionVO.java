package com.agentdoc.evaluation.pojo.vo;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetVersionEntity;
import java.time.LocalDateTime;
public record DatasetVersionVO(Long id, Long datasetId, Long spaceId, Integer versionNo, String status,
                               String contentHash, LocalDateTime publishedAt, Long createdBy) {
    public static DatasetVersionVO from(EvaluationDatasetVersionEntity value) {
        return new DatasetVersionVO(value.getId(), value.getDatasetId(), value.getSpaceId(), value.getVersionNo(),
                value.getStatus(), value.getContentHash(), value.getPublishedAt(), value.getCreatedBy());
    }
}
