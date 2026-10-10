package com.agentdoc.common.utils;

import com.agentdoc.common.feign.vo.AgentOnlineOriginalTextVO;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.List;

/** 原始文本身份固定协议；正文只通过 SHA-256 进入信封。 */
public final class OnlineOriginalTextUtils {
    public static final int SCHEMA_VERSION = 2;
    public static final String AVAILABLE = "AVAILABLE";
    public static final String UNAVAILABLE = "EVIDENCE_UNAVAILABLE";
    private OnlineOriginalTextUtils() { }

    public static String identityHash(AgentOnlineOriginalTextVO value) {
        return OnlineProtocolUtils.hash("online.original-text", Map.ofEntries(
                Map.entry("evidenceId", value.evidenceId()), Map.entry("taskId", value.taskId()),
                Map.entry("executionId", value.executionId()), Map.entry("spaceId", value.spaceId()),
                Map.entry("agentId", value.agentId()), Map.entry("experimentId", value.experimentId()),
                Map.entry("assignmentId", value.assignmentId()), Map.entry("bindingHash", value.bindingHash()),
                Map.entry("contentHash", value.contentHash()), Map.entry("capturedAt", value.capturedAt())));
    }

    public static boolean valid(AgentOnlineOriginalTextVO value) {
        if (value == null || value.schemaVersion() != SCHEMA_VERSION || !AVAILABLE.equals(value.state())
                || value.originalText() == null || value.identityHash() == null) { return false; }
        try {
            for (var id : List.of(value.evidenceId(), value.taskId(), value.executionId(), value.spaceId(),
                    value.agentId(), value.experimentId(), value.assignmentId())) { OnlineProtocolUtils.id(id); }
            LocalDateTime.parse(value.capturedAt());
            if (!value.evidenceId().equals(value.executionId()) || value.bindingHash() == null
                    || !value.bindingHash().matches("[0-9a-f]{64}")) { return false; }
            return StableSnapshotUtils.sha256Utf8(value.originalText()).equals(value.contentHash())
                    && identityHash(value).equals(value.identityHash());
        } catch (RuntimeException invalid) { return false; }
    }
}
