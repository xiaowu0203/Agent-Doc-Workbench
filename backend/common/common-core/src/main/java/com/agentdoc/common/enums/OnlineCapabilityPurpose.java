package com.agentdoc.common.enums;

/** 每种线上服务凭证使用独立受众；不能互换用途。 */
public enum OnlineCapabilityPurpose {
    /** 同一实验的授权续签及派生。 */ ONLINE_CONTROL("online-experiment-control"),
    /** 既存 Task/执行/账本事实只读。 */ ONLINE_OBSERVE("online-experiment-observe"),
    /** 既存 Task 的尽力取消。 */ ONLINE_CANCEL("online-experiment-cancel");

    /** 唯一允许的 JWT 受众。 */
    private final String audience;
    OnlineCapabilityPurpose(String audience) { this.audience = audience; }
    public String audience() { return audience; }
}
