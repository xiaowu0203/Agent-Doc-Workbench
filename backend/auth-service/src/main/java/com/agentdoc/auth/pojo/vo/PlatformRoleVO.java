package com.agentdoc.auth.pojo.vo;

import com.agentdoc.auth.pojo.entity.PlatformRoleEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 平台角色信息。
 */
@Schema(description = "平台角色信息")
public record PlatformRoleVO(
        @Schema(description = "平台角色 ID")
        Long id,

        @Schema(description = "平台角色稳定技术标识")
        String roleKey,

        @Schema(description = "平台角色展示名称")
        String displayName,

        @Schema(description = "是否为受保护角色")
        Boolean protectedRole,

        @Schema(description = "创建时间")
        LocalDateTime createdAt,

        @Schema(description = "更新时间")
        LocalDateTime updatedAt
) {

    public static PlatformRoleVO from(PlatformRoleEntity entity) {
        return new PlatformRoleVO(entity.getId(), entity.getRoleKey(), entity.getDisplayName(),
                entity.getProtectedRole(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
