package com.agentdoc.document.pojo.vo;

import com.agentdoc.document.enums.SpaceRole;
import com.agentdoc.document.enums.SpaceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 空间视图对象。
 */
@Schema(description = "空间信息")
public record SpaceVO(

        @Schema(description = "空间 ID")
        Long id,

        @Schema(description = "空间名称")
        String name,

        @Schema(description = "空间描述")
        String description,

        @Schema(description = "所有者用户 ID")
        Long ownerId,

        @Schema(description = "空间全局 Token 预算")
        Long tokenBudget,

        @Schema(description = "空间状态")
        SpaceStatus status,

        @Schema(description = "当前登录用户在该空间的角色")
        SpaceRole role,

        @Schema(description = "创建时间")
        LocalDateTime createdAt
) {
}
