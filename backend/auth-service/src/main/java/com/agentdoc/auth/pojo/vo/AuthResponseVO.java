package com.agentdoc.auth.pojo.vo;

import com.agentdoc.common.constant.JwtConstant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

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
        UserVO user,

        @Schema(description = "当前用户的平台角色标识集合")
        List<String> platformRoles
) {

    public static AuthResponseVO of(String accessToken, String refreshToken, long expiresIn, UserVO user,
                                    List<String> platformRoles) {
        return new AuthResponseVO(accessToken, refreshToken, JwtConstant.TOKEN_TYPE_BEARER, expiresIn, user,
                platformRoles == null ? List.of() : List.copyOf(platformRoles));
    }
}
