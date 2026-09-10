package com.agentdoc.common.feign.dto;

import com.agentdoc.common.enums.SpaceRoleAuditAction;

/**
 * 空间角色变更审计写入请求。
 *
 * @param spaceId 所属空间 ID
 * @param roleId 目标角色 ID
 * @param action 审计动作
 * @param detail 脱敏变更详情 JSON
 */
public record SpaceRoleAuditDTO(
        Long spaceId,
        Long roleId,
        SpaceRoleAuditAction action,
        String detail) {
}
