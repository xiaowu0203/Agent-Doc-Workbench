package com.agentdoc.evaluation.pojo.dto;

import com.agentdoc.evaluation.enums.EvaluationFeedbackLabel;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

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
 * @param onlineQuality 线上原始质量标注，省略时保留既有离线反馈语义
 */
@Schema(description = "创建人工评估反馈")
@JsonIgnoreProperties(ignoreUnknown = true)
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
        String comment,

        @Schema(description = "线上原始质量标注，可选")
        OnlineQualityFeedbackDTO onlineQuality
) {
    /** 线上扩展使用严格 JSON；旧 score 小数保持原有取值。 */
    public static EvaluationFeedbackCreateDTO parse(String json) {
        try {
            JsonNode node = JsonUtils.parse(json, JsonNode.class);
            if (node == null || !node.isObject()) { throw new IllegalArgumentException(); }
            OnlineQualityFeedbackDTO quality = null;
            if (node.has("onlineQuality")) {
                node = JsonUtils.parseStrict(json, JsonNode.class);
                if (node == null) { throw new IllegalArgumentException(); }
            }
            if (node.hasNonNull("onlineQuality")) {
                var fields = Set.of("spaceId", "caseRunId", "taskId", "executionId", "label", "score", "comment", "onlineQuality");
                node.fieldNames().forEachRemaining(name -> { if (!fields.contains(name)) { throw new IllegalArgumentException(); } });
                for (String field : new String[]{"spaceId", "taskId", "executionId", "caseRunId"}) {
                    if (node.hasNonNull(field)) {
                        var id = node.get(field);
                        if (!id.isTextual() && !id.isIntegralNumber()) { throw new IllegalArgumentException(); }
                        OnlineProtocolUtils.id(id.asText());
                    }
                }
                if (!node.path("label").isTextual() || node.hasNonNull("comment") && !node.get("comment").isTextual()
                        || node.hasNonNull("score") && !node.get("score").isNumber()) { throw new IllegalArgumentException(); }
                if (node.hasNonNull("comment")) { OnlineProtocolUtils.canonicalNode(node.get("comment")); }
                quality = OnlineQualityFeedbackDTO.parse(JsonUtils.toJson(node.get("onlineQuality")));
            }
            var value = JsonUtils.parse(json, EvaluationFeedbackCreateDTO.class);
            if (value == null) { throw new IllegalArgumentException(); }
            return new EvaluationFeedbackCreateDTO(value.spaceId(), value.caseRunId(), value.taskId(), value.executionId(),
                    value.label(), value.score(), value.comment(), quality);
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "人工反馈格式无效");
        }
    }
}
