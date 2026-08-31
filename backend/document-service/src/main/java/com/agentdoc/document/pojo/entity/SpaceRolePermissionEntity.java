package com.agentdoc.document.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 空间角色权限绑定实体。
 */
@Data
@TableName("space_role_permission")
@Schema(description = "空间角色权限绑定实体")
public class SpaceRolePermissionEntity {

    @Schema(description = "空间角色 ID")
    private Long roleId;

    @Schema(description = "权限标识符")
    private String permissionCode;

    @Schema(description = "绑定时间")
    private LocalDateTime createdAt;

    public static SpaceRolePermissionEntity of(Long roleId, String permissionCode) {
        SpaceRolePermissionEntity entity = new SpaceRolePermissionEntity();
        entity.setRoleId(roleId);
        entity.setPermissionCode(permissionCode);
        return entity;
    }
}
