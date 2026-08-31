package com.agentdoc.document.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改自定义空间角色请求。
 */
@Schema(description = "修改自定义空间角色请求")
public record SpaceRoleUpdateDTO(
        @Schema(description = "角色展示名称")
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 100, message = "角色名称不能超过 100 个字符")
        String displayName,
        @Schema(description = "角色说明")
        @Size(max = 255, message = "角色说明不能超过 255 个字符")
        String description
) {
}
