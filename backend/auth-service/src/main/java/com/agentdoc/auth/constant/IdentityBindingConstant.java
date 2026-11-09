package com.agentdoc.auth.constant;

/** 身份绑定状态。 */
public final class IdentityBindingConstant {
    public static final String ACTIVE = "ACTIVE";
    public static final String REVOKED = "REVOKED";
    public static final int ISSUER_MAX_LENGTH = 512;
    public static final int SUBJECT_MAX_LENGTH = 255;
    public static final String AUDIT_BOUND = "IDENTITY_BOUND";
    public static final String AUDIT_REVOKED = "IDENTITY_REVOKED";
    public static final String AUDIT_TARGET = "IDENTITY_BINDING";
    private IdentityBindingConstant() { }
}
