package com.agentdoc.common.constant;

import java.util.Set;

import static com.agentdoc.common.constant.SpacePermissionConstant.*;
import static com.agentdoc.common.constant.SpacePermissionConstant.AGENT_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.AUDIT_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.MCP_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.SKILL_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.USAGE_READ;

/**
 * 平台角色稳定标识符。
 */
public final class PlatformRoleConstant {

    private PlatformRoleConstant() {
    }

    /** 平台超级管理员：拥有全部空间级人类权限。 */
    public static final String SUPER_ADMIN = "PLATFORM_SUPER_ADMIN";

    /**
     * 平台超级管理员可跨空间读取的权限集合
     * <p>平台超管拥有这些权限时，可以读取任意空间对应资源，仅限读类权限，不开放写操作</p>
     */
    public static final Set<String> PLATFORM_CROSS_SPACE_READ_PERMISSIONS = Set.of(
            SPACE_READ, MEMBER_READ, ROLE_READ, DOCUMENT_READ, AGENT_READ, SKILL_READ,
            MCP_READ, TASK_READ, CHANGE_REQUEST_READ, USAGE_READ, AUDIT_READ);
}
