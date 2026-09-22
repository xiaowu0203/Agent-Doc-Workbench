package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_feedback")
@Schema(description = "不可变人工评估反馈")
public class EvaluationFeedbackEntity extends BaseEntity {
    private Long spaceId;
    private Long runId;
    private Long caseRunId;
    private Long taskId;
    private Long executionId;
    private String sourceType;
    private String sourceBusinessId;
    private String sourceHash;
    private String label;
    private BigDecimal score;
    private String comment;
    private String factsJson;
    private Long createdBy;
}
