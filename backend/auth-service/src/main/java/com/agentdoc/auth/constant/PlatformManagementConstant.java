package com.agentdoc.auth.constant;

/**
 * 平台管理模块常量类
 * 包含部门、用户、角色相关ID、长度限制、正则、事件消息常量
 */
public final class PlatformManagementConstant {

    /**
     * 根部门ID
     */
    public static final long ROOT_DEPARTMENT_ID = 0L;

    /**
     * 部门默认排序序号
     */
    public static final int DEFAULT_DEPARTMENT_SORT_ORDER = 0;

    /**
     * 人工操作主体类型
     */
    public static final int HUMAN_ACTOR_TYPE = 1;

    /**
     * 平台角色最大数量限制
     */
    public static final int MAX_PLATFORM_ROLE_COUNT = 1;

    /**
     * 搜索关键词最大长度
     */
    public static final int MAX_SEARCH_KEYWORD_LENGTH = 100;

    /**
     * 用户名最大长度
     */
    public static final int MAX_USERNAME_LENGTH = 32;

    /**
     * 用户名最小长度
     */
    public static final int MIN_USERNAME_LENGTH = 3;

    /**
     * 密码最大长度
     */
    public static final int MAX_PASSWORD_LENGTH = 64;

    /**
     * 密码最小长度
     */
    public static final int MIN_PASSWORD_LENGTH = 6;

    /**
     * 用户昵称最大长度
     */
    public static final int MAX_NICKNAME_LENGTH = 50;

    /**
     * 邮箱最大长度
     */
    public static final int MAX_EMAIL_LENGTH = 100;

    /**
     * 职位名称最大长度
     */
    public static final int MAX_JOB_TITLE_LENGTH = 100;

    /**
     * 部门名称最大长度
     */
    public static final int MAX_DEPARTMENT_NAME_LENGTH = 100;

    /**
     * 部门编码最大长度
     */
    public static final int MAX_DEPARTMENT_CODE_LENGTH = 64;

    /**
     * 用户名正则：仅允许大小写字母、数字、下划线
     */
    public static final String USERNAME_PATTERN = "^[a-zA-Z0-9_]+$";

    /**
     * 部门编码正则：仅允许大写字母、数字、下划线、横杠
     */
    public static final String DEPARTMENT_CODE_PATTERN = "^[A-Z0-9_-]+$";

    // ===================== 事件消息常量 =====================
    /**
     * 平台用户创建事件
     */
    public static final String USER_CREATED = "PLATFORM_USER_CREATED";
    /**
     * 平台用户更新事件
     */
    public static final String USER_UPDATED = "PLATFORM_USER_UPDATED";
    /**
     * 平台用户状态变更事件
     */
    public static final String USER_STATUS_CHANGED = "PLATFORM_USER_STATUS_CHANGED";
    /**
     * 平台用户密码重置事件
     */
    public static final String USER_PASSWORD_RESET = "PLATFORM_USER_PASSWORD_RESET";
    /**
     * 平台用户角色替换事件
     */
    public static final String USER_ROLES_REPLACED = "PLATFORM_USER_ROLES_REPLACED";
    /**
     * 平台角色创建事件
     */
    public static final String PLATFORM_ROLE_CREATED = "PLATFORM_ROLE_CREATED";
    /**
     * 平台角色更新事件
     */
    public static final String PLATFORM_ROLE_UPDATED = "PLATFORM_ROLE_UPDATED";
    /**
     * 平台角色删除事件
     */
    public static final String PLATFORM_ROLE_DELETED = "PLATFORM_ROLE_DELETED";
    /**
     * 部门创建事件
     */
    public static final String DEPARTMENT_CREATED = "DEPARTMENT_CREATED";
    /**
     * 部门更新事件
     */
    public static final String DEPARTMENT_UPDATED = "DEPARTMENT_UPDATED";
    /**
     * 部门删除事件
     */
    public static final String DEPARTMENT_DELETED = "DEPARTMENT_DELETED";

    // ===================== 操作目标类型常量 =====================
    /**
     * 操作目标：用户
     */
    public static final String USER_TARGET = "USER";
    /**
     * 操作目标：平台角色
     */
    public static final String PLATFORM_ROLE_TARGET = "PLATFORM_ROLE";
    /**
     * 操作目标：部门
     */
    public static final String DEPARTMENT_TARGET = "DEPARTMENT";

    private PlatformManagementConstant() {
    }
}
