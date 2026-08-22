package com.agentdoc.auth.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 认证响应：Access Token + Refresh Token + 用户信息。
 */
@Schema(description = "认证响应")
public record AuthResponseVO(
        @Schema(description = "访问令牌（短期 JWT）")
        String accessToken,

        @Schema(description = "刷新令牌（不透明随机串）")
        String refreshToken,

        @Schema(description = "令牌类型")
        String tokenType,

        @Schema(description = "访问令牌有效期（秒）")
        long expiresIn,

        @Schema(description = "用户信息")
        UserVO user
) {
}
