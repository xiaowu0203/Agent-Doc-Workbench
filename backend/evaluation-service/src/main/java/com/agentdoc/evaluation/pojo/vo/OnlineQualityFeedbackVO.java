package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 线上人工质量信息；审批过程反馈和历史离线记录不生成该视图。 */
@Schema(description = "线上原始产物的人工评分及更正身份")
public record OnlineQualityFeedbackVO(
        @Schema(description = "assignment ID，十进制文本") String assignmentId,
        @Schema(description = "原始证据类型") String originalEvidenceType,
        @Schema(description = "原始证据 ID，十进制文本") String originalEvidenceId,
        @Schema(description = "原始证据 SHA-256") String originalEvidenceHash,
        @Schema(description = "质量等级0～4，0为有效评分") Integer rubricScore,
        @Schema(description = "保留前序知情偏差后的状态") String blindness,
        @Schema(description = "调用者声明的知情状态") String declaredBlindness,
        @Schema(description = "被更正的前序 ID，可选") String supersedesFeedbackId,
        @Schema(description = "原因代码") List<String> reasonCodes,
        @Schema(description = "授权评审确认的严重安全事件") Boolean seriousSafetyEvent,
        @Schema(description = "严重安全确认理由") String safetyReason) {
    public static OnlineQualityFeedbackVO from(EvaluationFeedbackEntity value) {
        if (value.getRubricScore() == null) { return null; }
        var facts = JsonUtils.parse(value.getFactsJson(), JsonNode.class);
        return new OnlineQualityFeedbackVO(value.getOnlineAssignmentId().toString(), facts.path("originalEvidenceType").asText(),
                value.getOriginalEvidenceId().toString(), value.getOriginalEvidenceHash(), value.getRubricScore(), value.getBlindness(),
                facts.path("declaredBlindness").asText(), value.getSupersedesFeedbackId() == null ? null : value.getSupersedesFeedbackId().toString(),
                JsonUtils.parse(value.getReasonCodesJson(), new TypeReference<List<String>>() {}), value.getSeriousSafetyEvent(), value.getSafetyReason());
    }
}
