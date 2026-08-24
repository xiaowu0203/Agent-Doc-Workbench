package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.pojo.entity.ModelEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 模型元数据视图。
 */
@Schema(description = "模型元数据信息")
public record ModelVO(
        @Schema(description = "模型 ID") Long id,
        @Schema(description = "模型厂商") String provider,
        @Schema(description = "模型调用 key") String modelKey,
        @Schema(description = "模型展示名称") String displayName,
        @Schema(description = "模型官网链接") String officialUrl,
        @Schema(description = "上下文窗口大小") Long contextWindow,
        @Schema(description = "最大输出 token 数") Long maxOutputTokens,
        @Schema(description = "输入单价，元/百万 token") BigDecimal inputPricePerMillion,
        @Schema(description = "输出单价，元/百万 token") BigDecimal outputPricePerMillion,
        @Schema(description = "模型状态：1 启用 / 0 禁用") Integer status) {

    public static ModelVO from(ModelEntity entity) {
        return new ModelVO(entity.getId(), entity.getProvider(), entity.getModelKey(), entity.getDisplayName(),
                entity.getOfficialUrl(), entity.getContextWindow(), entity.getMaxOutputTokens(),
                entity.getInputPricePerMillion(), entity.getOutputPricePerMillion(), entity.getStatus());
    }
}
