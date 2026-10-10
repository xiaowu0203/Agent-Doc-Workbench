package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record OnlineExperimentStateDTO(
        @Schema(description = "动作幂等键") String clientRequestKey,
        @Schema(description = "冻结清单摘要") String manifestHash,
        @Schema(description = "非负十进制状态版本") String expectedStateVersion,
        @Schema(description = "人工操作原因") String reason) { }
