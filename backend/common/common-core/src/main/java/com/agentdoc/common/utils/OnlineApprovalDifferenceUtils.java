package com.agentdoc.common.utils;

import com.agentdoc.common.feign.vo.ChangeRequestFeedbackSnapshotVO;
import com.agentdoc.common.feign.vo.OnlineApprovalDifferenceVO;
import com.agentdoc.common.feign.vo.OnlineApprovalSourceVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 固定审批差异与反馈哈希协议，不改变历史审批摘要。 */
public final class OnlineApprovalDifferenceUtils {
    public static final int SCHEMA_VERSION = 2;
    public static final String ALGORITHM = "online-approval-codepoint-lcs-v1";
    public static final String COMPARABLE = "COMPARABLE";
    public static final String NOT_COMPARABLE = "NOT_COMPARABLE";
    public static final String NOT_MERGED = "NOT_MERGED";
    public static final String MULTIPLE_SOURCES = "MULTIPLE_SOURCES";
    public static final String ORIGINAL_UNAVAILABLE = "ORIGINAL_EVIDENCE_UNAVAILABLE";
    public static final String ORIGINAL_INVALID = "ORIGINAL_EVIDENCE_INVALID";
    public static final String TASK_NOT_COMPLETED = "TASK_NOT_COMPLETED";
    public static final String BASE_VERSION_MISMATCH = "BASE_VERSION_MISMATCH";
    public static final String MERGED_UNAVAILABLE = "MERGED_EVIDENCE_UNAVAILABLE";
    public static final String MERGED_INVALID = "MERGED_EVIDENCE_INVALID";
    public static final String DIFF_LIMIT_EXCEEDED = "DIFF_LIMIT_EXCEEDED";
    private static final Set<String> REASONS = Set.of(NOT_MERGED, MULTIPLE_SOURCES, ORIGINAL_UNAVAILABLE,
            ORIGINAL_INVALID, TASK_NOT_COMPLETED, BASE_VERSION_MISMATCH, MERGED_UNAVAILABLE, MERGED_INVALID, DIFF_LIMIT_EXCEEDED);
    private OnlineApprovalDifferenceUtils() { }

    public static OnlineApprovalDifferenceVO signed(OnlineApprovalSourceVO source, String reason,
            String originalContent, String mergedContent, Integer added, Integer deleted, Integer segments, BigDecimal ratio) {
        var value = new OnlineApprovalDifferenceVO(SCHEMA_VERSION, reason == null ? COMPARABLE : NOT_COMPARABLE,
                reason, ALGORITHM, source.input(), source.changeRequestId(), source.originalEvidenceHash(),
                source.originalIdentityHash(), source.baseVersion(), originalContent, source.mergedVersion(), mergedContent,
                source.mergedBy(), source.mergedAt(), added, deleted, segments, ratio, null);
        return copy(value, identityHash(value));
    }

    public static String identityHash(OnlineApprovalDifferenceVO value) {
        var payload = JsonUtils.parseStrict(JsonUtils.toJson(copy(value, null)), ObjectNode.class);
        // schema 2 不允许浮点哈希；比例固定为 10 位十进制字符串，跨 JSON 往返不依赖 double。
        if (value.humanModificationRatio() != null) {
            payload.put("humanModificationRatio", value.humanModificationRatio().setScale(10, RoundingMode.UNNECESSARY).toPlainString());
        }
        return OnlineProtocolUtils.hash("online.approval-difference", Map.of("difference", payload));
    }

    public static boolean valid(OnlineApprovalDifferenceVO value) {
        if (value == null || value.input() == null) { return false; }
        try {
            var input = value.input();
            for (String id : List.of(input.taskId(), input.executionId(), input.spaceId(), input.agentId(), input.experimentId(),
                    input.assignmentId(), input.documentId(), value.changeRequestId())) { OnlineProtocolUtils.id(id); }
            if (value.mergedBy() != null) { OnlineProtocolUtils.id(value.mergedBy()); }
            if (value.mergedAt() != null) { LocalDateTime.parse(value.mergedAt()); }
            if (value.schemaVersion() != SCHEMA_VERSION || !ALGORITHM.equals(value.algorithm()) || !"FORMAL".equals(input.documentType())
                    || !sha(input.bindingHash()) || !sha(input.inputHash()) || input.baseVersion() == null || input.baseVersion() < 0
                    || !optionalSha(value.originalEvidenceHash()) || !optionalSha(value.originalIdentityHash())
                    || !optionalSha(value.originalContentHash()) || !optionalSha(value.mergedContentHash())) { return false; }
            boolean metrics = COMPARABLE.equals(value.state()) && value.reason() == null && input.completed()
                    && Objects.equals(value.baseVersion(), input.baseVersion()) && value.mergedVersion() != null && value.mergedVersion() > value.baseVersion()
                    && value.mergedBy() != null && value.mergedAt() != null && sha(value.originalEvidenceHash()) && sha(value.originalIdentityHash())
                    && sha(value.originalContentHash()) && sha(value.mergedContentHash()) && value.addedCharacters() != null && value.addedCharacters() >= 0
                    && value.deletedCharacters() != null && value.deletedCharacters() >= 0 && value.changedSegments() != null && value.changedSegments() >= 0
                    && value.humanModificationRatio() != null && value.humanModificationRatio().signum() >= 0
                    && value.humanModificationRatio().compareTo(BigDecimal.ONE) <= 0;
            boolean missing = NOT_COMPARABLE.equals(value.state()) && REASONS.contains(value.reason())
                    && value.addedCharacters() == null && value.deletedCharacters() == null && value.changedSegments() == null && value.humanModificationRatio() == null;
            return (metrics || missing) && identityHash(value).equals(value.identityHash());
        } catch (RuntimeException invalid) { return false; }
    }

    public static boolean matches(OnlineApprovalSourceVO source, OnlineApprovalDifferenceVO value) {
        return valid(value) && source.input().equals(value.input()) && Objects.equals(source.changeRequestId(), value.changeRequestId())
                && Objects.equals(source.originalEvidenceHash(), value.originalEvidenceHash()) && Objects.equals(source.originalIdentityHash(), value.originalIdentityHash())
                && Objects.equals(source.baseVersion(), value.baseVersion()) && Objects.equals(source.mergedVersion(), value.mergedVersion())
                && Objects.equals(source.mergedBy(), value.mergedBy()) && Objects.equals(source.mergedAt(), value.mergedAt())
                && (source.reason() == null || source.reason().equals(value.reason()));
    }

    public static String feedbackHash(ChangeRequestFeedbackSnapshotVO value) {
        String old = StableSnapshotUtils.snapshotHash(1, Map.ofEntries(Map.entry("changeRequestId", value.changeRequestId()),
                Map.entry("status", value.status()), Map.entry("resolutionType", empty(value.resolutionType())),
                Map.entry("reviewCommentHash", empty(value.reviewCommentHash())), Map.entry("reviewedBy", empty(value.reviewedBy())),
                Map.entry("reviewedAt", value.reviewedAt() == null ? "" : value.reviewedAt().toString()), Map.entry("revisionNo", empty(value.revisionNo()))));
        return value.onlineApprovalDifference() == null ? old : OnlineProtocolUtils.hash("online.approval-feedback",
                Map.of("approvalHash", old, "differenceHash", value.onlineApprovalDifference().identityHash()));
    }

    private static OnlineApprovalDifferenceVO copy(OnlineApprovalDifferenceVO value, String hash) {
        return new OnlineApprovalDifferenceVO(value.schemaVersion(), value.state(), value.reason(), value.algorithm(), value.input(),
                value.changeRequestId(), value.originalEvidenceHash(), value.originalIdentityHash(), value.baseVersion(), value.originalContentHash(),
                value.mergedVersion(), value.mergedContentHash(), value.mergedBy(), value.mergedAt(), value.addedCharacters(),
                value.deletedCharacters(), value.changedSegments(), value.humanModificationRatio(), hash);
    }
    private static boolean sha(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean optionalSha(String value) { return value == null || sha(value); }
    private static Object empty(Object value) { return value == null ? "" : value; }
}
