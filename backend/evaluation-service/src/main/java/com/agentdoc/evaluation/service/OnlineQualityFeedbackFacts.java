package com.agentdoc.evaluation.service;

import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.pojo.dto.OnlineQualityFeedbackDTO;
import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 从不可变列复核原评分摘要；报告与安全消费都不信任孤立的score或事件标志。 */
public final class OnlineQualityFeedbackFacts {
    private OnlineQualityFeedbackFacts() { }
    /** 只校验拓扑并按更正顺序返回；来源摘要与业务身份由各入口另行复核。 */
    public static List<EvaluationFeedbackEntity> orderedChain(List<EvaluationFeedbackEntity> rows) {
        if (rows.isEmpty()) { return List.of(); }
        var byId = new HashMap<Long, EvaluationFeedbackEntity>();
        var children = new HashMap<Long, EvaluationFeedbackEntity>();
        EvaluationFeedbackEntity root = null;
        for (var row : rows) {
            if (row == null || row.getId() == null || byId.put(row.getId(), row) != null) { return null; }
            if (row.getSupersedesFeedbackId() == null) {
                if (root != null) { return null; } root = row;
            } else if (children.put(row.getSupersedesFeedbackId(), row) != null) { return null; }
        }
        var visited = new HashSet<Long>(); var ordered = new ArrayList<EvaluationFeedbackEntity>();
        var cursor = root;
        while (cursor != null) {
            if (!visited.add(cursor.getId())) { return null; }
            ordered.add(cursor); cursor = children.get(cursor.getId());
        }
        return ordered.size() == rows.size() ? List.copyOf(ordered) : null;
    }
    public static boolean valid(EvaluationFeedbackEntity row) {
        try {
            if (row == null || !"MANUAL".equals(row.getSourceType()) || row.getId() == null || row.getCreatedBy() == null
                    || row.getRunId() != null || row.getCaseRunId() != null || row.getOnlineAssignmentId() == null || row.getOriginalEvidenceId() == null) { return false; }
            var facts = OnlineProtocolUtils.object(row.getFactsJson());
            if (facts.path("qualitySchemaVersion").asInt() != 2) { return false; }
            var input = JsonUtils.parseStrict(JsonUtils.toJson(facts.path("input")), OnlineOriginalInputVO.class);
            if (!Objects.equals(input.assignmentId(), row.getOnlineAssignmentId().toString()) || !Objects.equals(input.taskId(), row.getTaskId().toString())
                    || !Objects.equals(input.executionId(), row.getExecutionId().toString()) || !Objects.equals(input.spaceId(), row.getSpaceId().toString()) || !input.completed()) { return false; }
            var codes = new ArrayList<String>(); JsonUtils.parse(row.getReasonCodesJson(), JsonNode.class).forEach(code -> codes.add(code.asText()));
            var quality = new OnlineQualityFeedbackDTO(input.assignmentId(), facts.path("originalEvidenceType").asText(), row.getOriginalEvidenceId().toString(),
                    row.getOriginalEvidenceHash(), row.getRubricScore(), facts.path("declaredBlindness").asText(),
                    row.getSupersedesFeedbackId() == null ? null : row.getSupersedesFeedbackId().toString(), codes, row.getSeriousSafetyEvent(), row.getSafetyReason());
            quality.validate(); if (row.getScore() == null || row.getScore().compareTo(quality.normalizedScore()) != 0) { return false; }
            var payload = new LinkedHashMap<String, Object>(Map.ofEntries(Map.entry("feedbackId", row.getId().toString()), Map.entry("input", input),
                    Map.entry("quality", quality), Map.entry("originalIdentityHash", facts.path("originalIdentityHash").asText()),
                    Map.entry("effectiveBlindness", row.getBlindness()), Map.entry("label", row.getLabel()), Map.entry("score", quality.normalizedScore().toPlainString()),
                    Map.entry("comment", row.getComment() == null ? "" : row.getComment()), Map.entry("actorId", row.getCreatedBy().toString())));
            String domain = "online.quality-feedback";
            if (facts.has("exposureId")) {
                payload.put("exposureId", facts.path("exposureId").asText()); payload.put("exposureHash", facts.path("exposureHash").asText());
                domain = "online.quality-feedback-with-exposure";
            }
            if (facts.has("reviewReceiptId")) {
                payload.put("reviewReceiptId", facts.path("reviewReceiptId").asText()); payload.put("reviewReceiptHash", facts.path("reviewReceiptHash").asText());
                domain = "online.quality-feedback-with-review-proof";
            } else if ("BLINDED".equals(row.getBlindness())) { return false; }
            return row.getSourceHash().equals(OnlineProtocolUtils.hash(domain, payload));
        } catch (RuntimeException damaged) { return false; }
    }
}
