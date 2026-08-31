package com.agentdoc.document.pojo.vo;

import com.agentdoc.document.pojo.entity.SpaceRoleEntity;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 空间角色摘要。
 */
@Schema(description = "空间角色摘要")
public record SpaceRoleSummaryVO(
        @Schema(description = "角色 ID") Long roleId,
        @Schema(description = "稳定角色标识") String roleKey,
        @Schema(description = "角色展示名称") String displayName
) {
    public static SpaceRoleSummaryVO from(SpaceRoleEntity entity) {
        return new SpaceRoleSummaryVO(entity.getId(), entity.getRoleKey(), entity.getDisplayName());
    }
}
