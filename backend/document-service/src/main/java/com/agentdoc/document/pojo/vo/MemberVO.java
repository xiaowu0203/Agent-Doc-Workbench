package com.agentdoc.document.pojo.vo;

import com.agentdoc.common.enums.SpaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 空间成员视图对象。
 */
@Schema(description = "空间成员信息")
public record MemberVO(

        @Schema(description = "成员记录 ID")
        Long id,

        @Schema(description = "用户 ID")
        Long userId,

        @Schema(description = "成员角色")
        SpaceRole role,

        @Schema(description = "加入时间")
        LocalDateTime createdAt
) {
}
