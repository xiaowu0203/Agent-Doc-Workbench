package com.agentdoc.document.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建自定义空间角色请求。
 */
@Schema(description = "创建自定义空间角色请求")
public record SpaceRoleCreateDTO(
        @Schema(description = "稳定技术标识，小写 kebab-case")
        @NotBlank(message = "角色标识不能为空")
        @Pattern(regexp = "^[a-z][a-z0-9-]{1,63}$", message = "角色标识必须为 kebab-case")
        String roleKey,

        @Schema(description = "角色展示名称")
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 100, message = "角色名称不能超过 100 个字符")
        String displayName,

        @Schema(description = "角色说明")
        @Size(max = 255, message = "角色说明不能超过 255 个字符")
        String description,

        @Schema(description = "权限标识符列表")
        @NotEmpty(message = "角色权限不能为空")
        List<String> permissionCodes
) {
}
