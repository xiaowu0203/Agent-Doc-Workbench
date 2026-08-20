package com.agentdoc.auth.dto;

/**
 * 认证响应：Access Token + Refresh Token + 用户信息。
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserDto user
) {
}