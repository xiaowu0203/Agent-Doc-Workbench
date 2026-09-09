package com.agentdoc.auth.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台管理侧用户信息。
 */
@Schema(description = "平台管理侧用户信息")
public record PlatformUserVO(
        @Schema(description = "用户 ID") Long id,
        @Schema(description = "用户名") String username,
        @Schema(description = "昵称") String nickname,
        @Schema(description = "邮箱") String email,
        @Schema(description = "头像地址") String avatarUrl,
        @Schema(description = "所属部门") DepartmentSummaryVO department,
        @Schema(description = "职位") String jobTitle,
        @Schema(description = "状态：0 禁用 / 1 启用") Integer status,
        @Schema(description = "平台角色标识集合") List<String> platformRoles,
        @Schema(description = "最后登录时间") LocalDateTime lastLoginAt,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt
) {
}
