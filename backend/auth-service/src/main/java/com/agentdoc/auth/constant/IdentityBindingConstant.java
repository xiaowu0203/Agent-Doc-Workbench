package com.agentdoc.auth.constant;

/**
 * 身份绑定状态。
 * <p>
 * OIDC外部身份绑定表相关常量，用于维护外部IdP身份(iss+sub)和平台内部userId之间的绑定关系，
 * 包含绑定状态枚举值、字段长度限制、审计日志事件标识。
 * </p>
 */
public final class IdentityBindingConstant {

    /** 绑定生效状态，可以正常用于OIDC登录映射 */
    public static final String ACTIVE = "ACTIVE";
    /** 绑定已撤销，不再允许通过该外部身份登录 */
    public static final String REVOKED = "REVOKED";

    /** 数据库issuer字段最大长度限制 */
    public static final int ISSUER_MAX_LENGTH = 512;
    /** 数据库subject(sub)字段最大长度限制 */
    public static final int SUBJECT_MAX_LENGTH = 255;

    /** 审计事件：新增身份绑定 */
    public static final String AUDIT_BOUND = "IDENTITY_BOUND";
    /** 审计事件：撤销身份绑定 */
    public static final String AUDIT_REVOKED = "IDENTITY_REVOKED";
    /** 审计日志资源目标类型：身份绑定 */
    public static final String AUDIT_TARGET = "IDENTITY_BINDING";

    private IdentityBindingConstant() { }
}
