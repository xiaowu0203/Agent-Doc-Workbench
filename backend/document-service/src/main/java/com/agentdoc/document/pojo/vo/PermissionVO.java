package com.agentdoc.document.pojo.vo;

import com.agentdoc.document.pojo.entity.PermissionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 空间权限目录视图。
 */
@Schema(description = "空间权限目录")
public record PermissionVO(
        @Schema(description = "稳定权限标识符") String code,
        @Schema(description = "权限展示名称") String name,
        @Schema(description = "权限分类") String category,
        @Schema(description = "权限说明") String description,
        @Schema(description = "展示顺序") Integer sortOrder
) {
    public static PermissionVO from(PermissionEntity entity) {
        return new PermissionVO(entity.getCode(), entity.getName(), entity.getCategory(),
                entity.getDescription(), entity.getSortOrder());
    }
}
