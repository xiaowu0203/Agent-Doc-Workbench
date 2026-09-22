package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_metric")
@Schema(description = "标准化不可变评估业务指标")
public class EvaluationMetricEntity extends BaseEntity {
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "EvaluationRun ID") private Long runId;
    @Schema(description = "CaseRun ID") private Long caseRunId;
    @Schema(description = "CaseAttempt ID") private Long caseAttemptId;
    @Schema(description = "TestCaseVersion ID") private Long testCaseVersionId;
    @Schema(description = "EvaluationResult ID；执行型为空") private Long evaluationResultId;
    @Schema(description = "EvaluatorVersion ID；执行型为空") private Long evaluatorVersionId;
    @Schema(description = "Metric 信封契约版本") private Integer contractVersion;
    @Schema(description = "来源：EVALUATOR / EXECUTION") private String source;
    @Schema(description = "生产者 ID：Result ID 或 Attempt ID") private Long producerId;
    @Schema(description = "稳定 Metric key") private String metricKey;
    @Schema(description = "值类型：NUMBER / BOOLEAN / STRING") private String valueType;
    @Schema(description = "数值型值") private BigDecimal numericValue;
    @Schema(description = "布尔型值") private Boolean booleanValue;
    @Schema(description = "受控字符串值") private String stringValue;
    @Schema(description = "标准单位或实际 ISO-4217 币种") private String unit;
    @Schema(description = "比较方向") private String direction;
}
