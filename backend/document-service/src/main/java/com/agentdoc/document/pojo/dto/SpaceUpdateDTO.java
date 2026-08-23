package com.agentdoc.document.pojo.dto;

import com.agentdoc.document.enums.SpaceStatus;
import com.agentdoc.document.pojo.entity.SpaceEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 更新空间请求参数（字段为空则不更新）。
 */
@Schema(description = "更新空间请求")
public record SpaceUpdateDTO(

        @Schema(description = "空间名称")
        @Size(max = 100, message = "空间名称最长 100 字符")
        String name,

        @Schema(description = "空间描述")
        @Size(max = 500, message = "空间描述最长 500 字符")
        String description,

        @Schema(description = "空间全局 Token 预算（Phase 3 熔断用）")
        Long tokenBudget,

        @Schema(description = "空间状态：NORMAL 正常 / DISABLED 禁用")
        SpaceStatus status
) {

    /**
     * 将非空字段应用到实体（局部更新，null 字段不覆盖）。
     * @param entity 目标空间实体
     */
    public void applyTo(SpaceEntity entity) {
        if (name != null) {
            entity.setName(name);
        }
        if (description != null) {
            entity.setDescription(description);
        }
        if (tokenBudget != null) {
            entity.setTokenBudget(tokenBudget);
        }
        if (status != null) {
            entity.setStatus(status.getCode());
        }
    }
}
