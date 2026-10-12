package com.agentdoc.common.utils;

import com.agentdoc.common.feign.vo.OnlineOriginalChangeItemVO;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.feign.vo.OnlineOriginalChangesVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.Objects;

/** 线上原始变更身份协议；敏感载荷只通过摘要进入信封。 */
public final class OnlineOriginalChangeUtils {
    public static final int SCHEMA_VERSION = 2;
    public static final String AVAILABLE = "AVAILABLE";
    public static final String UNAVAILABLE = "EVIDENCE_UNAVAILABLE";
    public static final String INVALID = "ORIGINAL_EVIDENCE_INVALID";
    public static final String PROPOSAL = "CHANGE_REQUEST";
    public static final String DRAFT = "DRAFT_DOCUMENT";
    private OnlineOriginalChangeUtils() { }

    public static OnlineOriginalChangeItemVO signed(OnlineOriginalInputVO input, String id, String type,
            Long base, Long output, String payload, String content, boolean valid, String captured) {
        var item = new OnlineOriginalChangeItemVO(id, type, base, output, payload, content, valid, captured, null);
        return new OnlineOriginalChangeItemVO(id, type, base, output, payload, content, valid, captured, identityHash(input, item));
    }

    public static String identityHash(OnlineOriginalInputVO input, OnlineOriginalChangeItemVO item) {
        return OnlineProtocolUtils.hash("online.original-change", Map.of("input", input,
                "evidence", new OnlineOriginalChangeItemVO(item.evidenceId(), item.outputType(), item.baseVersion(),
                item.outputVersion(), item.payloadHash(), item.contentHash(), item.structureValid(), item.capturedAt(), null)));
    }

    public static boolean valid(OnlineOriginalInputVO input, OnlineOriginalChangeItemVO item) {
        if (input == null || item == null) { return false; }
        try {
            for (String id : List.of(input.taskId(), input.executionId(), input.spaceId(), input.agentId(),
                    input.experimentId(), input.assignmentId(), input.documentId(), item.evidenceId())) { OnlineProtocolUtils.id(id); }
            LocalDateTime.parse(item.capturedAt());
            return sha(input.bindingHash()) && sha(input.inputHash()) && input.baseVersion() != null && input.baseVersion() >= 0
                    && Set.of(PROPOSAL, DRAFT).contains(item.outputType()) && sha(item.payloadHash())
                    && (PROPOSAL.equals(item.outputType()) ? "FORMAL".equals(input.documentType()) : "DRAFT".equals(input.documentType()))
                    && (!item.structureValid() || sha(item.contentHash()))
                    && (item.contentHash() == null || sha(item.contentHash()))
                    && identityHash(input, item).equals(item.identityHash());
        } catch (RuntimeException invalid) { return false; }
    }

    /** 完整任务产物集合身份；失败提案仍参与摘要，排序不依赖数据库返回顺序。 */
    public static String setIdentityHash(OnlineOriginalChangesVO source) {
        if (source == null || source.schemaVersion() != SCHEMA_VERSION || !AVAILABLE.equals(source.state())
                || source.input() == null || !source.input().completed() || source.items() == null || source.items().isEmpty()
                || source.items().stream().anyMatch(item -> !valid(source.input(), item))
                || source.items().stream().map(OnlineOriginalChangeItemVO::evidenceId).distinct().count() != source.items().size()) {
            throw new IllegalArgumentException("原始变更集合身份无效");
        }
        var input = source.input();
        for (var item : source.items()) {
            boolean formal = PROPOSAL.equals(item.outputType());
            if (item.baseVersion() == null || item.baseVersion() < 0 || (formal
                    ? item.outputVersion() != null || (item.structureValid()
                        ? !Objects.equals(item.baseVersion(), input.baseVersion()) : item.contentHash() != null)
                    : source.items().size() != 1 || !item.structureValid() || !Objects.equals(item.baseVersion(), input.baseVersion())
                        || input.baseVersion() == Long.MAX_VALUE
                        || !Objects.equals(item.outputVersion(), Math.addExact(input.baseVersion(), 1L))
                        || !Objects.equals(item.payloadHash(), item.contentHash()))) {
                throw new IllegalArgumentException("原始变更集合版本或结构身份无效");
            }
        }
        var sorted = source.items().stream().sorted(Comparator.comparing(item -> OnlineProtocolUtils.id(item.evidenceId()))).toList();
        return OnlineProtocolUtils.hash("online.original-change-set", Map.of("input", input, "items", sorted));
    }

    private static boolean sha(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
}
