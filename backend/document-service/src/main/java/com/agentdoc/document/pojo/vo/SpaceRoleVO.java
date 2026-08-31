package com.agentdoc.document.pojo.vo;

import com.agentdoc.document.pojo.entity.SpaceRoleEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 空间角色详情。
 */
@Schema(description = "空间角色详情")
public record SpaceRoleVO(
        @Schema(description = "角色 ID") Long id,
        @Schema(description = "所属空间 ID") Long spaceId,
        @Schema(description = "稳定角色标识") String roleKey,
        @Schema(description = "角色展示名称") String displayName,
        @Schema(description = "角色说明") String description,
        @Schema(description = "是否为受保护默认角色") Boolean protectedRole,
        @Schema(description = "权限标识符列表") List<String> permissionCodes,
        @Schema(description = "创建时间") LocalDateTime createdAt
) {
    public static SpaceRoleVO from(SpaceRoleEntity entity, List<String> permissionCodes) {
        return new SpaceRoleVO(entity.getId(), entity.getSpaceId(), entity.getRoleKey(),
                entity.getDisplayName(), entity.getDescription(), entity.getProtectedRole(),
                List.copyOf(permissionCodes), entity.getCreatedAt());
    }
}
