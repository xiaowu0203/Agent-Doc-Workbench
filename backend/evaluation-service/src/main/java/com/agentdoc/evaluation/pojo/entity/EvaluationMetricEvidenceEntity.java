package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_metric_evidence")
@Schema(description = "标准 Metric 与证据引用关联")
public class EvaluationMetricEvidenceEntity extends BaseEntity {
    @Schema(description = "Metric ID") private Long metricId;
    @Schema(description = "EvidenceReference ID") private Long evidenceReferenceId;
}
