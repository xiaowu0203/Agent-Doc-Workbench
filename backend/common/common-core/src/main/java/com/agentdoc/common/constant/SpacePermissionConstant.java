package com.agentdoc.common.constant;

/**
 * 空间权限常量
 * <p>
 * 权限字符串格式：资源:操作，用于鉴权、角色权限绑定、权限校验逻辑
 * 必须与数据库 {@code permission.code} 保持一致
 * </p>
 */
public final class SpacePermissionConstant {

    private SpacePermissionConstant() {
    }

    // ====================== 空间相关权限 ======================
    /** 空间-读取权限：查看空间基础信息 */
    public static final String SPACE_READ = "space:read";
    /** 空间-管理权限：编辑空间配置、基础信息设置 */
    public static final String SPACE_MANAGE = "space:manage";
    /** 空间-删除权限：执行空间删除操作 */
    public static final String SPACE_DELETE = "space:delete";

    // ====================== 成员相关权限 ======================
    /** 成员-读取权限：查看空间成员列表、成员信息 */
    public static final String MEMBER_READ = "member:read";
    /** 成员-管理权限：新增、移除、修改空间成员 */
    public static final String MEMBER_MANAGE = "member:manage";

    // ====================== 角色相关权限 ======================
    /** 角色-读取权限：查看角色列表、角色权限详情 */
    public static final String ROLE_READ = "role:read";
    /** 角色-管理权限：创建、编辑、删除自定义角色 */
    public static final String ROLE_MANAGE = "role:manage";

    // ====================== 文档相关权限 ======================
    /** 文档-读取权限：查看文档内容、文档列表 */
    public static final String DOCUMENT_READ = "document:read";
    /** 文档-创建权限：新建文档 */
    public static final String DOCUMENT_CREATE = "document:create";
    /** 文档-编辑权限：修改已有文档内容 */
    public static final String DOCUMENT_EDIT = "document:edit";

    // ====================== Agent智能体相关权限 ======================
    /** Agent-读取权限：查看智能体列表、智能体配置信息 */
    public static final String AGENT_READ = "agent:read";
    /** Agent-管理权限：创建、修改、删除智能体基础配置 */
    public static final String AGENT_MANAGE = "agent:manage";
    /** Agent‑绑定Skill权限：为智能体绑定/解绑Skill插件 */
    public static final String AGENT_BIND_SKILL = "agent:bind_skill";
    /** Agent‑绑定MCP权限：为智能体绑定/解绑MCP服务实例 */
    public static final String AGENT_BIND_MCP = "agent:bind_mcp";

    // ====================== Skill插件相关权限 ======================
    /** Skill-读取权限：查看Skill包列表、详情、元信息 */
    public static final String SKILL_READ = "skill:read";
    /** Skill-管理权限：上传、版本管理、删除Skill插件包 */
    public static final String SKILL_MANAGE = "skill:manage";

    // ====================== MCP服务相关权限 ======================
    /** MCP-读取权限：查看MCP服务实例列表与配置 */
    public static final String MCP_READ = "mcp:read";
    /** MCP-管理权限：新增、编辑、删除MCP服务实例 */
    public static final String MCP_MANAGE = "mcp:manage";

    // ====================== 任务相关权限 ======================
    /** 任务-读取权限：查看任务列表、任务执行日志与状态 */
    public static final String TASK_READ = "task:read";
    /** 任务-创建权限：发起新建任务 */
    public static final String TASK_CREATE = "task:create";
    /** 任务-终止权限：中断正在运行的任务 */
    public static final String TASK_TERMINATE = "task:terminate";

    // ====================== 变更申请相关权限 ======================
    /** 变更申请-读取权限：查看变更申请记录、详情 */
    public static final String CHANGE_REQUEST_READ = "change_request:read";
    /** 变更申请-提交权限：发起提交变更申请 */
    public static final String CHANGE_REQUEST_SUBMIT = "change_request:submit";
    /** 变更申请-审批权限：对变更申请执行审批同意/驳回 */
    public static final String CHANGE_REQUEST_APPROVE = "change_request:approve";
    /** 变更申请-合并权限：审批通过后执行变更合并落地 */
    public static final String CHANGE_REQUEST_MERGE = "change_request:merge";

    // ====================== 用量、审计相关权限 ======================
    /** 用量-读取权限：查询资源调用、模型消耗用量统计 */
    public static final String USAGE_READ = "usage:read";
    /** 审计-读取权限：查看操作审计日志记录 */
    public static final String AUDIT_READ = "audit:read";
}
