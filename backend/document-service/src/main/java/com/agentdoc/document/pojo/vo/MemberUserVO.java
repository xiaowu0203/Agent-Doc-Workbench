package com.agentdoc.document.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 空间成员用户摘要。
 */
@Schema(description = "空间成员用户摘要")
public record MemberUserVO(

        @Schema(description = "用户 ID")
        Long userId,

        @Schema(description = "用户名")
        String username,

        @Schema(description = "用户昵称")
        String nickname
) {
}
