package com.agentdoc.evaluation.pojo.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

public record OnlineExperimentReportVO(
        @Schema(description = "报告身份") String id,
        @Schema(description = "实验身份") String experimentId,
        @Schema(description = "版本") Integer revision,
        @Schema(description = "ONLINE_V1") String reportType,
        @Schema(description = "报告schema") Integer schemaVersion,
        @Schema(description = "报告摘要") String reportHash,
        @Schema(description = "计算输入摘要") String inputHash,
        @Schema(description = "SUPPORTED/UNSUPPORTED_SCHEMA/HASH_MISMATCH") String state,
        @Schema(description = "校验通过的脱敏报告；不兼容时为空") JsonNode report) { }
