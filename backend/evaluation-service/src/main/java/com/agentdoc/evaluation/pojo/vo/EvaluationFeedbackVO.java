package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "人工评估反馈记录")
public record EvaluationFeedbackVO(
        @Schema(description = "反馈ID")
        Long id,

        @Schema(description = "所属空间ID")
        Long spaceId,

        @Schema(description = "评估运行ID，可选")
        Long runId,

        @Schema(description = "评估用例运行ID，可选")
        Long caseRunId,

        @Schema(description = "工作台任务ID，可选")
        Long taskId,

        @Schema(description = "Agent执行记录ID，可选")
        Long executionId,

        @Schema(description = "反馈来源类型")
        String sourceType,

        @Schema(description = "来源业务标识")
        String sourceBusinessId,

        @Schema(description = "来源内容哈希")
        String sourceHash,

        @Schema(description = "反馈标签")
        String label,

        @Schema(description = "人工打分，0.0~1.0")
        BigDecimal score,

        @Schema(description = "反馈备注")
        String comment,

        @Schema(description = "结构化定位信息 JSON")
        String factsJson,

        @Schema(description = "创建人ID")
        Long createdBy,

        @Schema(description = "创建时间")
        LocalDateTime createdAt
) {
    /**
     * 实体转VO
     * @param value 人工反馈数据库实体
     * @return 对外视图对象
     */
    public static EvaluationFeedbackVO from(EvaluationFeedbackEntity value) {
        return new EvaluationFeedbackVO(value.getId(), value.getSpaceId(), value.getRunId(), value.getCaseRunId(),
                value.getTaskId(), value.getExecutionId(), value.getSourceType(), value.getSourceBusinessId(),
                value.getSourceHash(), value.getLabel(), value.getScore(), value.getComment(), value.getFactsJson(),
                value.getCreatedBy(), value.getCreatedAt());
    }
}
