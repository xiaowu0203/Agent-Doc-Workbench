package com.agentdoc.document.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 整体替换角色权限请求。
 */
@Schema(description = "整体替换角色权限请求")
public record RolePermissionReplaceDTO(
        @Schema(description = "权限标识符列表")
        @NotEmpty(message = "角色权限不能为空")
        List<String> permissionCodes
) {
}
