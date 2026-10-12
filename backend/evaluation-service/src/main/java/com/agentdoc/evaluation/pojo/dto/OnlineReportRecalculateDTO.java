package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record OnlineReportRecalculateDTO(
        @Schema(description = "操作者范围的幂等键") String clientRequestKey,
        @Schema(description = "冻结的接受顺序，十进制文本，允许0") String assignmentCutoffSequence,
        @Schema(description = "明确的ISO本地观察截止时间，毫秒精度") String observationCutoffAt) { }
