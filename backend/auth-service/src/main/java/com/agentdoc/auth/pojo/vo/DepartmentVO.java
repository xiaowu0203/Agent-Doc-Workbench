package com.agentdoc.auth.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 部门信息。树形关系由 parentId 表达，调用方按需组装。
 */
@Schema(description = "部门信息")
public record DepartmentVO(
        @Schema(description = "部门 ID") Long id,
        @Schema(description = "上级部门 ID；0 为根节点") Long parentId,
        @Schema(description = "部门名称") String name,
        @Schema(description = "部门编码") String code,
        @Schema(description = "负责人用户 ID") Long leaderUserId,
        @Schema(description = "负责人名称") String leaderName,
        @Schema(description = "直属成员数量") long memberCount,
        @Schema(description = "直属子部门数量") long childCount,
        @Schema(description = "同级排序值") Integer sortOrder,
        @Schema(description = "状态：0 禁用 / 1 启用") Integer status,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt
) {
}
