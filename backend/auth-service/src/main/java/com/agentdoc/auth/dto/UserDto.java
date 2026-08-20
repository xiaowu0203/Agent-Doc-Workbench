package com.agentdoc.auth.dto;

/**
 * 用户信息 DTO。
 */
public record UserDto(
        Long id,
        String username,
        String nickname,
        String email,
        String avatarUrl
) {
}