package com.agentdoc.evaluation.pojo.dto;

import com.agentdoc.evaluation.enums.EvaluationFeedbackLabel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 人工评估反馈创建DTO
 * <p>
 * 为评估运行/原始任务提交人工标注反馈，可关联用例运行记录或原始执行任务；
 * score取值区间 [0.0,1.0]，用于后续指标聚合与模型效果分析。
 *
 * @param spaceId     归属空间ID
 * @param caseRunId   评估用例运行ID，可选，绑定评估回放记录
 * @param taskId      原始工作台任务ID，可选
 * @param executionId Agent执行记录ID，可选
 * @param label       反馈分类标签 {@link EvaluationFeedbackLabel}
 * @param score       人工打分，范围0.0~1.0
 * @param comment     文字备注，选填，最大2000字符
 */
@Schema(description = "创建人工评估反馈")
public record EvaluationFeedbackCreateDTO(
        @NotNull(message = "spaceId 不能为空")
        @Schema(description = "所属空间ID")
        Long spaceId,

        @Schema(description = "评估用例运行ID，二选一：caseRunId / taskId")
        Long caseRunId,

        @Schema(description = "原始工作台任务ID，二选一：caseRunId / taskId")
        Long taskId,

        @Schema(description = "Agent执行记录ID，可选")
        Long executionId,

        @NotNull(message = "反馈标签不能为空")
        @Schema(description = "反馈分类标签")
        EvaluationFeedbackLabel label,

        @DecimalMin(value = "0.0", message = "分数不能小于0.0")
        @DecimalMax(value = "1.0", message = "分数不能大于1.0")
        @Schema(description = "人工打分，取值 0.0 ~ 1.0", example = "0.85")
        BigDecimal score,

        @Size(max = 2000, message = "反馈备注不能超过2000字符")
        @Schema(description = "反馈文字备注，选填")
        String comment
) {}