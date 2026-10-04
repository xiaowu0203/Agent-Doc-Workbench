package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.agentdoc.evaluation.enums.OnlineRuleRole;
import com.agentdoc.evaluation.enums.OnlineComparison;
import com.agentdoc.evaluation.enums.OnlineEvidenceTarget;
import com.agentdoc.evaluation.enums.EvaluationMetricValueType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
@Schema(description = "OnlineRuleBindingDTO")
public record OnlineRuleBindingDTO(
        @Schema(description = "规则稳定标识") String ruleKey,
        @Schema(description = "规则角色") OnlineRuleRole role,
        @Schema(description = "发布版本身份") String evaluatorVersionId,
        @Schema(description = "指标标识") String metricKey,
        @Schema(description = "指标类型") EvaluationMetricValueType valueType,
        @Schema(description = "单位") String unit,
        @Schema(description = "比较符") OnlineComparison comparison,
        @Schema(description = "固定阈值") JsonNode threshold,
        @Schema(description = "原始证据目标") OnlineEvidenceTarget evidenceTarget,
        @Schema(description = "各文档冻结期望") List<OnlineRuleExpectedDTO> expectedBindings
) { }
