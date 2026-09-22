package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseEntity;

public record EvaluationTestCaseVO(Long id, Long spaceId, String name, String description,
                                   Boolean archived, Long createdBy) {
    public static EvaluationTestCaseVO from(EvaluationTestCaseEntity value) {
        return new EvaluationTestCaseVO(value.getId(), value.getSpaceId(), value.getName(), value.getDescription(),
                value.getArchived(), value.getCreatedBy());
    }
}
