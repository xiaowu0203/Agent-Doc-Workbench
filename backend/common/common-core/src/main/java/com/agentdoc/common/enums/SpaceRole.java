package com.agentdoc.common.enums;

/**
 * 空间成员角色（角色绑定到具体空间，非用户全局属性）。
 * <p>权限级别：OWNER(1) &gt; EDITOR(2) &gt; VIEWER(3)，数值越小权限越大。</p>
 */
public enum SpaceRole {

    /** 所有者：全权管理（成员权限、Agent 配置、Token 预算、空间设置）。 */
    OWNER(1, "所有者"),

    /** 编辑者：编辑文档、发起 Agent 任务、审批变更请求。 */
    EDITOR(2, "编辑者"),

    /** 观察者：仅只读查看，无编辑/审批权限。 */
    VIEWER(3, "观察者");

    /** 数据库存储及跨服务传输使用的角色编码。 */
    private final int code;

    /** 角色展示名称。 */
    private final String name;

    SpaceRole(int code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 按角色编码解析枚举，未知编码返回 null。
     *
     * @param code 角色编码
     * @return 对应枚举，未知返回 null
     */
    public static SpaceRole fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SpaceRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return null;
    }

    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
