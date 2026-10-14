package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OnlineSrmVO")
public record OnlineSrmVO(
        @Schema(description = "NOT_ENOUGH_UNITS/PASS/DETECTED") String status,
        @Schema(description = "精确二项概率十进制，未启用为空") String pValue
) { }
