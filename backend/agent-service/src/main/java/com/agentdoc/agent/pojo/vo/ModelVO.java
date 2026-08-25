package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.ModelStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "模型配置信息")
public record ModelVO(
        @Schema(description = "模型 ID") Long id,
        @Schema(description = "模型提供商") String provider,
        @Schema(description = "模型标识") String modelKey,
        @Schema(description = "模型展示名称") String displayName,
        @Schema(description = "官方文档地址") String officialUrl,
        @Schema(description = "模型服务基础地址") String baseUrl,
        @Schema(description = "是否已配置 API Key") boolean apiKeyConfigured,
        @Schema(description = "上下文窗口大小") Long contextWindow,
        @Schema(description = "最大输出 Token 数") Long maxOutputTokens,
        @Schema(description = "输入价格（每百万 Token）") BigDecimal inputPricePerMillion,
        @Schema(description = "输出价格（每百万 Token）") BigDecimal outputPricePerMillion,
        @Schema(description = "状态") ModelStatus status,
        @Schema(description = "模型描述") String description) {
}
