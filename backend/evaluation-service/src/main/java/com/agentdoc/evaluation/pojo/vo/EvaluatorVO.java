package com.agentdoc.evaluation.pojo.vo;
import com.agentdoc.evaluation.pojo.entity.EvaluatorEntity;
public record EvaluatorVO(Long id, Long spaceId, String name, String evaluatorKey,
                          String description, Boolean archived, Long createdBy) {
    public static EvaluatorVO from(EvaluatorEntity value) {
        return new EvaluatorVO(value.getId(), value.getSpaceId(), value.getName(), value.getEvaluatorKey(),
                value.getDescription(), value.getArchived(), value.getCreatedBy());
    }
}
