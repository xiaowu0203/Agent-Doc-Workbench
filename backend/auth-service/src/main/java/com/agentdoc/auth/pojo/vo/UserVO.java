package com.agentdoc.auth.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户信息 VO（对外输出，剥离密码等敏感字段）。
 */
@Schema(description = "用户信息")
public record UserVO(
        @Schema(description = "用户 ID")
        Long id,

        @Schema(description = "用户名")
        String username,

        @Schema(description = "昵称")
        String nickname,

        @Schema(description = "邮箱")
        String email,

        @Schema(description = "头像地址")
        String avatarUrl
) {
}
