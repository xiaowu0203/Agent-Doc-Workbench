package com.agentdoc.document.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 平台管理侧用户空间参与情况。
 */
@Schema(description = "平台用户空间参与情况")
public record PlatformUserMembershipVO(
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "空间 ID") Long spaceId,
        @Schema(description = "空间名称") String spaceName,
        @Schema(description = "空间角色") SpaceRoleSummaryVO role
) {
}
