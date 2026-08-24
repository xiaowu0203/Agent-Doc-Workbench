package com.agentdoc.task.pojo.dto;

import com.agentdoc.task.enums.ModelStatus;
import com.agentdoc.task.pojo.entity.ModelEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * 模型元数据创建参数。
 */
@Schema(description = "模型元数据创建参数")
public record ModelCreateDTO(
        @Schema(description = "模型厂商", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String provider,
        @Schema(description = "模型调用 key", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String modelKey,
        @Schema(description = "模型展示名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String displayName,
        @Schema(description = "模型官网链接")
        String officialUrl,
        @Schema(description = "上下文窗口大小")
        Long contextWindow,
        @Schema(description = "最大输出 token 数")
        Long maxOutputTokens,
        @Schema(description = "输入单价，元/百万 token")
        BigDecimal inputPricePerMillion,
        @Schema(description = "输出单价，元/百万 token")
        BigDecimal outputPricePerMillion) {

    /**
     * 转换为默认启用的模型实体。
     *
     * @return 模型实体
     */
    public ModelEntity toEntity() {
        ModelEntity entity = new ModelEntity();
        entity.setProvider(provider);
        entity.setModelKey(modelKey);
        entity.setDisplayName(displayName);
        entity.setOfficialUrl(officialUrl);
        entity.setContextWindow(contextWindow);
        entity.setMaxOutputTokens(maxOutputTokens);
        entity.setInputPricePerMillion(inputPricePerMillion);
        entity.setOutputPricePerMillion(outputPricePerMillion);
        entity.setStatus(ModelStatus.ENABLED.getCode());
        return entity;
    }
}
