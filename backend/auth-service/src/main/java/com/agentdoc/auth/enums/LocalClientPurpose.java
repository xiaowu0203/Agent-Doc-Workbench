package com.agentdoc.auth.enums;

/** 登记机器调用方的唯一签发用途，不从请求体接受。 */
public enum LocalClientPurpose {
    /** BFF提交用户密码换取仅服务端持有的人类凭据。 */
    BFF,
    /** 后台服务申请自己的有限期身份凭据。 */
    SERVICE
}
