package com.agentdoc.evaluation.pojo.dto;

import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.OnlineOriginalChangeUtils;
import com.agentdoc.evaluation.enums.EvaluationEvidenceType;
import com.agentdoc.evaluation.enums.OnlineFeedbackBlindness;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.math.BigDecimal;

/** 线上原始质量标注；新增协议字段不接受 JSON 标量自动转换。 */
@Schema(description = "线上不可变原始产物的人工质量反馈")
public record OnlineQualityFeedbackDTO(
        @Schema(description = "实际 assignment ID，规范十进制文本") String assignmentId,
        @Schema(description = "原始证据类型 ORIGINAL_TEXT/CHANGE_REQUEST/DRAFT_DOCUMENT/ORIGINAL_CHANGE_SET") String originalEvidenceType,
        @Schema(description = "原始证据 ID；集合使用实际 assignmentId") String originalEvidenceId,
        @Schema(description = "原始载荷 SHA-256；集合使用 online.original-change-set 身份摘要") String originalEvidenceHash,
        @Schema(description = "人工质量等级，0～4 整数") Integer rubricScore,
        @Schema(description = "声明的 BLINDED/NON_BLINDED/UNKNOWN 状态") String blindness,
        @Schema(description = "被明确更正的当前链尾反馈 ID，可选") String supersedesFeedbackId,
        @Schema(description = "不重复的 ASCII 大写原因代码，最多20项") List<String> reasonCodes,
        @Schema(description = "是否由授权评审确认严重安全事件") Boolean seriousSafetyEvent,
        @Schema(description = "安全确认理由，最长2000字符") String safetyReason) {
    private static final Set<String> FIELDS = Set.of("assignmentId", "originalEvidenceType", "originalEvidenceId",
            "originalEvidenceHash", "rubricScore", "blindness", "supersedesFeedbackId", "reasonCodes", "seriousSafetyEvent", "safetyReason");
    private static final Set<String> TYPES = Set.of(EvaluationEvidenceType.ORIGINAL_TEXT.name(), EvaluationEvidenceType.ORIGINAL_CHANGE_SET.name(),
            OnlineOriginalChangeUtils.PROPOSAL, OnlineOriginalChangeUtils.DRAFT);
    private static final Set<String> BLINDNESS = Set.of(OnlineFeedbackBlindness.BLINDED.name(), OnlineFeedbackBlindness.NON_BLINDED.name(), OnlineFeedbackBlindness.UNKNOWN.name());
    private static final int MAX_REASONS = 20;
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int MAX_RUBRIC = 4;

    public static OnlineQualityFeedbackDTO parse(String json) {
        var node = OnlineProtocolUtils.object(json);
        node.fieldNames().forEachRemaining(name -> { if (!FIELDS.contains(name)) { throw invalid(); } });
        String assignment = id(node, "assignmentId"), evidence = id(node, "originalEvidenceId");
        String type = text(node, "originalEvidenceType"), hash = text(node, "originalEvidenceHash");
        String blind = text(node, "blindness"); var rubric = node.path("rubricScore"); var safety = node.path("seriousSafetyEvent");
        if (!TYPES.contains(type) || !hash.matches("[0-9a-f]{64}") || !BLINDNESS.contains(blind)
                || !rubric.isIntegralNumber() || !rubric.canConvertToInt() || rubric.intValue() < 0 || rubric.intValue() > MAX_RUBRIC
                || !safety.isBoolean()) { throw invalid(); }
        String supersedes = optionalText(node, "supersedesFeedbackId");
        if (supersedes != null) { OnlineProtocolUtils.id(supersedes); }
        var codes = node.path("reasonCodes");
        if (!codes.isArray() || codes.size() > MAX_REASONS) { throw invalid(); }
        var reasons = new ArrayList<String>(); var unique = new HashSet<String>();
        for (var code : codes) {
            if (!code.isTextual() || !code.textValue().matches("[A-Z][A-Z0-9_]{0,63}") || !unique.add(code.textValue())) { throw invalid(); }
            reasons.add(code.textValue());
        }
        String safetyReason = optionalText(node, "safetyReason");
        if (safetyReason != null && safetyReason.length() > MAX_TEXT_LENGTH) { throw invalid(); }
        if (safety.booleanValue() ? safetyReason == null || safetyReason.isBlank() : safetyReason != null && !safetyReason.isBlank()) { throw invalid(); }
        return new OnlineQualityFeedbackDTO(assignment, type, evidence, hash, rubric.intValue(), blind, supersedes,
                List.copyOf(reasons), safety.booleanValue(), safetyReason);
    }
    public void validate() { parse(JsonUtils.toJson(this)); }
    public BigDecimal normalizedScore() { return BigDecimal.valueOf(rubricScore).divide(BigDecimal.valueOf(MAX_RUBRIC)); }
    private static String id(JsonNode node, String field) { String value = text(node, field); OnlineProtocolUtils.id(value); return value; }
    private static String text(JsonNode node, String field) { if (!node.path(field).isTextual()) { throw invalid(); } return node.path(field).textValue(); }
    private static String optionalText(JsonNode node, String field) { return !node.hasNonNull(field) ? null : text(node, field); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("线上质量反馈格式无效"); }
}
