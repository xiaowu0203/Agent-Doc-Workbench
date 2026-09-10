package com.agentdoc.auth.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。
 */
@Schema(description = "登录请求")
public record LoginRequestDTO(
        @Schema(description = "用户名")
        @NotBlank(message = "用户名不能为空")
        String username,

        @Schema(description = "明文密码")
        @NotBlank(message = "密码不能为空")
        String password,

        @Schema(description = "是否在浏览器关闭后保持登录")
        Boolean remember
) {

    public boolean shouldRemember() {
        return Boolean.TRUE.equals(remember);
    }
}
