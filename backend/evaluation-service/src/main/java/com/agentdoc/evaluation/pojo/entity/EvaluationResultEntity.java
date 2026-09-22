package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_result")
@Schema(description = "不可变评估结果")
public class EvaluationResultEntity extends BaseEntity {
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "EvaluationRun ID") private Long runId;
    @Schema(description = "CaseAttempt ID") private Long caseAttemptId;
    @Schema(description = "EvaluatorVersion ID") private Long evaluatorVersionId;
    @Schema(description = "同一 Evaluator 的评价尝试序号") private Integer evaluationAttemptNo;
    @Schema(description = "结果状态") private String status;
    @Schema(description = "非权威便捷展示分数") private BigDecimal score;
    @Schema(description = "稳定摘要码") private String summaryCode;
    @Schema(description = "私有诊断详情 JSON；不得作为比较契约") private String detailsJson;
    @Schema(description = "内置规则实现版本") private String implementationVersion;
    @Schema(description = "评价 Trace ID") private String traceId;
    @Schema(description = "评价 Span ID") private String spanId;
    @Schema(description = "开始时间") private LocalDateTime startedAt;
    @Schema(description = "结束时间") private LocalDateTime finishedAt;
}
