package com.agentdoc.auth.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改平台角色请求。
 */
@Schema(description = "修改平台角色请求")
public record PlatformRoleUpdateDTO(
        @Schema(description = "平台角色展示名称")
        @NotBlank(message = "平台角色名称不能为空")
        @Size(max = 100, message = "平台角色名称不能超过 100 个字符")
        String displayName
) {
}
