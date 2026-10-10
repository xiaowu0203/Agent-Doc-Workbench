package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record OnlineExperimentStartDTO(
        @Schema(description = "动作幂等键") String clientRequestKey,
        @Schema(description = "冻结清单摘要") String manifestHash,
        @Schema(description = "非负十进制状态版本") String expectedStateVersion,
        @Schema(description = "有效权威预检证明") String preflightProofHash,
        @Schema(description = "确认 LIVE 副作用") boolean liveSideEffectsAcknowledged,
        @Schema(description = "确认产物保留") boolean retentionAcknowledged,
        @Schema(description = "确认共享资源干扰") boolean sharedResourcesAcknowledged,
        @Schema(description = "确认自动紧急取消") boolean emergencyCancellationAcknowledged) { }
