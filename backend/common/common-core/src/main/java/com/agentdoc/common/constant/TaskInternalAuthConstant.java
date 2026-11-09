package com.agentdoc.common.constant;

import java.util.List;

/** P0 Task 内部调用的过渡认证边界；不表示业务资源授权。 */
public final class TaskInternalAuthConstant {

    /** 独立机器主体，不接受同名人类账号代替。 */
    public static final String CLIENT_ID = "task-service";
    /** 机器凭据最少 UTF-8 字节数。 */
    public static final int MIN_SECRET_BYTES = 32;
    /** 仅标识已认证的 Task 服务，不能作为空间权限。 */
    public static final String AUTHORITY = "SERVICE_TASK";
    /** Task/Worker 签发入口。 */
    public static final List<String> SIGNING_PATHS = List.of(
            "/api/auth/internal/task-capabilities",
            "/api/auth/internal/evaluation-worker-capabilities");
    /** Task 创建/派发需要的执行档案入口。 */
    public static final String EXECUTION_PROFILE_PATH = "/api/agent/internal/agents/*/execution-profile";

    private TaskInternalAuthConstant() {
    }
}
