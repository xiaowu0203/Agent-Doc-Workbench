package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.ModelStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Agent 可选模型摘要")
public record ModelOptionVO(
        @Schema(description = "模型 ID") Long id,
        @Schema(description = "模型提供商") String provider,
        @Schema(description = "模型展示名称") String displayName,
        @Schema(description = "状态") ModelStatus status) {

    public static ModelOptionVO from(ModelVO model) {
        return new ModelOptionVO(model.id(), model.provider(), model.displayName(), model.status());
    }
}
