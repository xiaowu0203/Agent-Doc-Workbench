package com.agentdoc.common.utils;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.OnlineAssignmentRequestDTO;
import java.util.List;

/** HTTP 层禁止数字 ID/预算隐式转文本；业务事实仍必须由 Task 创建意图证明。 */
public final class OnlineAssignmentRequestParser {
    private OnlineAssignmentRequestParser() { }
    public static OnlineAssignmentRequestDTO parse(String json) {
        try {
            var node = OnlineProtocolUtils.object(json);
            for (String field : List.of("taskId", "spaceId", "agentId", "documentId", "actorId", "requestKey", "requestHash",
                    "inputHash", "documentVersion", "documentContentHash")) {
                if (!node.path(field).isTextual()) { throw invalid(); }
            }
            if (node.hasNonNull("tokenBudget") && !node.path("tokenBudget").isTextual()
                    || !node.path("inputSchemaVersion").isIntegralNumber()) { throw invalid(); }
            var request = JsonUtils.parseStrict(json, OnlineAssignmentRequestDTO.class); if (request == null) { throw invalid(); } return request;
        } catch (IllegalArgumentException invalid) { throw invalid(); }
    }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.BAD_REQUEST, OnlineReasonCode.MANIFEST_INVALID.name()); }
}
