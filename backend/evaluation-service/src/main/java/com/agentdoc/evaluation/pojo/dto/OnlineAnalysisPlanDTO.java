package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.agentdoc.evaluation.enums.OnlineAnalysisMode;
@Schema(description = "OnlineAnalysisPlanDTO")
public record OnlineAnalysisPlanDTO(
        @Schema(description = "分析模式") OnlineAnalysisMode mode,
        @Schema(description = "规范十进制最小差异") String mdeAbsoluteRatio,
        @Schema(description = "差异依据") String mdeReason,
        @Schema(description = "是否要求盲态人工质量") Boolean humanQualityRequired
) { }
