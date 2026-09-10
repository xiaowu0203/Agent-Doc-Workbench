package com.agentdoc.auth.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求。
 */
@Schema(description = "修改密码请求")
public record ChangePasswordRequestDTO(
        @Schema(description = "当前密码")
        @NotBlank(message = "当前密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度为 6-64")
        String currentPassword,

        @Schema(description = "新密码")
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度为 6-64")
        String newPassword
) {
}
