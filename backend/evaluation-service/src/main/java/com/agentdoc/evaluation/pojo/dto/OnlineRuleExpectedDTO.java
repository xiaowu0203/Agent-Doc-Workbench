package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.databind.JsonNode;
@Schema(description = "OnlineRuleExpectedDTO")
public record OnlineRuleExpectedDTO(
        @Schema(description = "文档身份") String documentId,
        @Schema(description = "冻结规则期望对象") JsonNode expectedJson
) { }
