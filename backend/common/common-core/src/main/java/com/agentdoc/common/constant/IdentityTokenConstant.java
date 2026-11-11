package com.agentdoc.common.constant;

import java.time.Duration;

/** 正式人类/服务身份协议；与业务Capability的issuer、密钥及用途独立。 */
public final class IdentityTokenConstant {
    private IdentityTokenConstant() { }
    /** local身份发行者，不由部署地址或请求头拼接。 */
    public static final String LOCAL_ISSUER = "urn:agent-doc-workbench:local-identity";
    /** local身份JWK kid域，后缀为公钥RFC7638指纹。 */
    public static final String LOCAL_KEY_PREFIX = "local-identity-";
    /** local人类JWT的JOSE typ。 */
    public static final String HUMAN_JOSE_TYPE = "adw-human+jwt";
    /** local服务JWT的JOSE typ。 */
    public static final String SERVICE_JOSE_TYPE = "adw-service+jwt";
    /** 载荷用途声明键。 */
    public static final String CLAIM_PURPOSE = "purpose";
    /** local人类载荷用途。 */
    public static final String HUMAN_PURPOSE = "HUMAN_ACCESS";
    /** local服务载荷用途。 */
    public static final String SERVICE_PURPOSE = "SERVICE_PROOF";
    /** 人类凭据签发时的本地认证版本。 */
    public static final String CLAIM_AUTH_VERSION = "auth_version";
    /** Auth生成的会话关联标识。 */
    public static final String CLAIM_SESSION_ID = "sid";
    /** 唯一登记BFF client，与OIDC客户端标识一致。 */
    public static final String BFF_CLIENT = JwtConstant.OIDC_HUMAN_CLIENT;
    /** 人类API资源组。 */
    public static final String HUMAN_AUDIENCE = JwtConstant.OIDC_HUMAN_AUDIENCE;
    /** 人类用途。 */
    public static final String HUMAN_SCOPE = JwtConstant.OIDC_HUMAN_SCOPE;
    /** 内部服务身份资源组；资源权限仍由对应业务裁决。 */
    public static final String SERVICE_AUDIENCE = "workbench-internal";
    /** 服务用途，不代表任意用户/动作权限。 */
    public static final String SERVICE_SCOPE = "workbench:service";
    /** 已确认人类绝对有效期上限。 */
    public static final Duration HUMAN_MAX_LIFETIME = Duration.ofDays(7);
    /** 已确认local/oidc服务证明寿命上限。 */
    public static final Duration SERVICE_MAX_LIFETIME = Duration.ofSeconds(60);
    /** 已确认偏差；服务已有证明最大残余为60+30秒。 */
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    /** 登记服务client的规范标识；不能用数字用户ID伪装服务。 */
    public static final String SERVICE_CLIENT_PATTERN = "[a-z][a-z0-9-]{1,63}";
}
