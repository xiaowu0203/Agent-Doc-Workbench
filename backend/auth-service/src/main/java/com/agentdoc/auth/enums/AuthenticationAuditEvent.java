package com.agentdoc.auth.enums;

/** 本地认证的追加事件，不把每次资源访问都当成新登录。 */
public enum AuthenticationAuditEvent {
    /** 用户密码认证及受限凭据交付成功。 */
    LOCAL_LOGIN_SUCCEEDED,
    /** 用户密码或状态校验未通过。 */
    LOCAL_LOGIN_FAILED,
    /** 达到登录失败/预占上限。 */
    LOCAL_LOGIN_THROTTLED,
    /** 认证所需依赖不可用。 */
    LOCAL_LOGIN_UNAVAILABLE,
    /** 登记服务的固定用途证明交付成功。 */
    LOCAL_SERVICE_PROOF_ISSUED,
    /** 机器认证或跨用途请求被拒绝。 */
    LOCAL_CLIENT_REJECTED
}
