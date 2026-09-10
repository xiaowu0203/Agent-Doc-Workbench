package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

@Schema(description = "模型配置更新参数")
public record ModelUpdateDTO(
        @NotBlank
        @Schema(description = "模型提供商", requiredMode = Schema.RequiredMode.REQUIRED)
        String provider,

        @Schema(description = "兼容字段；服务端按模型提供商和服务地址自动确定适配器")
        String adapterType,

        @NotBlank
        @Schema(description = "模型标识", requiredMode = Schema.RequiredMode.REQUIRED)
        String modelKey,

        @NotBlank
        @Schema(description = "模型展示名称", requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName,

        @Schema(description = "官方文档地址")
        String officialUrl,

        @Schema(description = "模型服务基础地址")
        String baseUrl,

        @Schema(description = "模型 API Key；为空时保留现有密钥", accessMode = Schema.AccessMode.WRITE_ONLY)
        String apiKey,

        @Schema(description = "适配器扩展配置 JSON")
        String optionsJson,

        @Schema(description = "上下文窗口大小")
        Long contextWindow,

        @Schema(description = "最大输出 Token 数")
        Long maxOutputTokens,

        @Schema(description = "输入价格（每百万 Token）")
        BigDecimal inputPricePerMillion,

        @Schema(description = "输出价格（每百万 Token）")
        BigDecimal outputPricePerMillion,

        @Schema(description = "模型描述")
        String description) {
}
