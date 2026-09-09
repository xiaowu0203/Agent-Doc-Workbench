package com.agentdoc.auth.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.agentdoc.auth.constant.PlatformManagementConstant.MAX_PASSWORD_LENGTH;
import static com.agentdoc.auth.constant.PlatformManagementConstant.MIN_PASSWORD_LENGTH;

/**
 * 管理员重置用户密码请求。
 */
@Schema(description = "管理员重置用户密码请求")
public record PlatformUserPasswordResetDTO(
        @NotBlank(message = "新密码不能为空")
        @Size(min = MIN_PASSWORD_LENGTH, max = MAX_PASSWORD_LENGTH, message = "密码长度为 6-64")
        @Schema(description = "新密码")
        String newPassword
) {
}
