package com.agentdoc.evaluation.pojo.vo;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetEntity;
import java.time.LocalDateTime;
public record EvaluationDatasetVO(Long id, Long spaceId, String name, String description,
                                  Boolean archived, Long createdBy, LocalDateTime createdAt) {
    public static EvaluationDatasetVO from(EvaluationDatasetEntity value) {
        return new EvaluationDatasetVO(value.getId(), value.getSpaceId(), value.getName(), value.getDescription(),
                value.getArchived(), value.getCreatedBy(), value.getCreatedAt());
    }
}
