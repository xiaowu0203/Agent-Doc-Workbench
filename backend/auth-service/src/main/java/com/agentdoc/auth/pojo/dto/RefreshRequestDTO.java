package com.agentdoc.auth.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌请求。
 */
@Schema(description = "刷新令牌请求")
public record RefreshRequestDTO(
        @Schema(description = "刷新令牌")
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken
) {
}
