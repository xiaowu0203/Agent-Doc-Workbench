package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

@Schema(description = "模型配置创建参数")
public record ModelCreateDTO(
        @NotBlank @Schema(description = "模型提供商", requiredMode = Schema.RequiredMode.REQUIRED) String provider,
        @Schema(description = "模型适配器类型；为空时按提供商推导") String adapterType,
        @NotBlank @Schema(description = "模型标识", requiredMode = Schema.RequiredMode.REQUIRED) String modelKey,
        @NotBlank @Schema(description = "模型展示名称", requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(description = "官方文档地址") String officialUrl,
        @Schema(description = "模型服务基础地址") String baseUrl,
        @NotBlank @Schema(description = "模型 API Key", requiredMode = Schema.RequiredMode.REQUIRED,
                accessMode = Schema.AccessMode.WRITE_ONLY) String apiKey,
        @Schema(description = "适配器扩展配置 JSON") String optionsJson,
        @Schema(description = "上下文窗口大小") Long contextWindow,
        @Schema(description = "最大输出 Token 数") Long maxOutputTokens,
        @Schema(description = "输入价格（每百万 Token）") BigDecimal inputPricePerMillion,
        @Schema(description = "输出价格（每百万 Token）") BigDecimal outputPricePerMillion,
        @Schema(description = "模型描述") String description) {
}
