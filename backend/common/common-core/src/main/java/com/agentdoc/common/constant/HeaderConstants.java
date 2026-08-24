package com.agentdoc.common.constant;

/**
 * 网关与业务服务之间透传的请求头常量
 */
public final class HeaderConstants {

    private HeaderConstants() {
    }

    /** 链路追踪 ID */
    public static final String X_TRACE_ID = "X-Trace-Id";

    /** task-service 生成的非对称签名任务能力令牌 */
    public static final String X_TASK_CAPABILITY = "X-Task-Capability";

}
