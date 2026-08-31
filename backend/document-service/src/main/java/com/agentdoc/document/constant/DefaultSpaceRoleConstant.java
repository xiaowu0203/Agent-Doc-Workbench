package com.agentdoc.document.constant;

import java.util.List;
import static com.agentdoc.common.constant.SpacePermissionConstant.*;

/**
 * 默认空间角色标识。
 */
public final class DefaultSpaceRoleConstant {

    private DefaultSpaceRoleConstant() {
    }
    // 所有者
    public static final String OWNER = "OWNER";
    // 编辑者
    public static final String EDITOR = "EDITOR";
    // 观察者
    public static final String VIEWER = "VIEWER";

    /** 观察者默认权限集合：只读空间资源，不包含成员和角色查看权限。 */
    public static final List<String> VIEWER_PERMISSIONS = List.of(
            SPACE_READ, DOCUMENT_READ, AGENT_READ, SKILL_READ,
            MCP_READ, TASK_READ, CHANGE_REQUEST_READ, USAGE_READ, AUDIT_READ);

    /**
     * 编辑者 默认权限集合：可读 + 文档/任务/变更请求编辑提交能力
     */
    public static final List<String> EDITOR_PERMISSIONS = List.of(
            SPACE_READ, MEMBER_READ, ROLE_READ, DOCUMENT_READ, DOCUMENT_CREATE, DOCUMENT_EDIT,
            AGENT_READ, SKILL_READ, MCP_READ, TASK_READ, TASK_CREATE, TASK_TERMINATE,
            CHANGE_REQUEST_READ, CHANGE_REQUEST_SUBMIT, CHANGE_REQUEST_APPROVE,
            CHANGE_REQUEST_MERGE, USAGE_READ, AUDIT_READ);
}
