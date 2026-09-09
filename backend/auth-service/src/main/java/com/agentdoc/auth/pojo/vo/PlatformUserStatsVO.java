package com.agentdoc.auth.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 平台用户统计。
 */
@Schema(description = "平台用户统计")
public record PlatformUserStatsVO(
        @Schema(description = "用户总数") long total,
        @Schema(description = "启用用户数") long enabled,
        @Schema(description = "禁用用户数") long disabled,
        @Schema(description = "未分配部门用户数") long unassignedDepartment
) {
}
