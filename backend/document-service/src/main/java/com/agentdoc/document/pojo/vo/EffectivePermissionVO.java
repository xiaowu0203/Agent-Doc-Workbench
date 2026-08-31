package com.agentdoc.document.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 当前用户在指定空间的有效权限视图。
 */
@Schema(description = "当前用户空间有效权限")
public record EffectivePermissionVO(
        @Schema(description = "空间 ID") Long spaceId,
        @Schema(description = "是否为平台超级管理员") boolean platformSuperAdmin,
        @Schema(description = "当前空间角色；平台超级管理员非成员时为空") SpaceRoleSummaryVO role,
        @Schema(description = "最终有效权限标识符") List<String> permissions
) {
}
