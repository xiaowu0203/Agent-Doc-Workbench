package com.agentdoc.common.enums;

/** 部署时选择的唯一认证模式，不支持请求级切换或凭据回退。 */
public enum AuthenticationMode {
    /** Workbench本地密码及服务端身份凭据。 */
    LOCAL,
    /** 单一受信Keycloak身份源。 */
    OIDC
}
