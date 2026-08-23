package com.agentdoc.document.pojo.dto;

import com.agentdoc.document.pojo.entity.SpaceEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建空间请求参数。
 */
@Schema(description = "创建空间请求")
public record SpaceCreateDTO(

        @Schema(description = "空间名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "空间名称不能为空")
        @Size(max = 100, message = "空间名称最长 100 字符")
        String name,

        @Schema(description = "空间描述")
        @Size(max = 500, message = "空间描述最长 500 字符")
        String description,

        @Schema(description = "空间全局 Token 预算（Phase 3 熔断用）")
        Long tokenBudget
) {

    /**
     * 转换为空间实体（创建人由服务层指定）。
     * @param ownerId 创建人（所有者）用户 ID
     * @return 空间实体
     */
    public SpaceEntity toEntity(Long ownerId) {
        SpaceEntity entity = new SpaceEntity();
        entity.setName(name);
        entity.setDescription(description);
        entity.setTokenBudget(tokenBudget);
        entity.setOwnerId(ownerId);
        return entity;
    }
}
