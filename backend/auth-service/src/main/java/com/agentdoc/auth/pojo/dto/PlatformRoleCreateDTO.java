package com.agentdoc.auth.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建平台角色请求。
 */
@Schema(description = "创建平台角色请求")
public record PlatformRoleCreateDTO(
        @Schema(description = "平台角色稳定技术标识")
        @NotBlank(message = "平台角色标识不能为空")
        @Size(max = 64, message = "平台角色标识不能超过 64 个字符")
        String roleKey,

        @Schema(description = "平台角色展示名称")
        @NotBlank(message = "平台角色名称不能为空")
        @Size(max = 100, message = "平台角色名称不能超过 100 个字符")
        String displayName
) {
}
