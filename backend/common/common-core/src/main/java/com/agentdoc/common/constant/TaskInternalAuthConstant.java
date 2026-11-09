package com.agentdoc.common.constant;

import java.util.List;

/**
 * P0 Task 内部调用的过渡认证边界；不表示业务资源授权。
 * <p>
 * 机器内部认证常量集合，配合 {@link TaskInternalAuthenticationFilter}、Feign 出方向拦截器配套使用；
 * 仅用于识别 Task 服务这个调用方主体，该身份只代表“是Task服务发起的内部调用”，
 * 本身不携带任何业务空间、数据权限，业务授权仍需要单独校验。
 * </p>
 */
public final class TaskInternalAuthConstant {

    /**
     * 独立机器主体，不接受同名人类账号代替。
     * Basic Auth 的 username，固定为 task-service。
     */
    public static final String CLIENT_ID = "task-service";

    /**
     * 机器凭据最少 UTF-8 字节数。
     * secret 低于该长度直接拒绝服务，防止弱密钥。
     */
    public static final int MIN_SECRET_BYTES = 32;

    /**
     * 权限标识：仅标识已认证的 Task 服务，不能作为空间权限。
     * 写入 SecurityContext 的 GrantedAuthority，仅用于区分机器调用身份，不可用于业务资源鉴权。
     */
    public static final String AUTHORITY = "SERVICE_TASK";

    /**
     * Task/Worker 签发入口路径列表。
     * 这些端点接受 Task 机器身份来申请能力令牌（task-capability、evaluation-worker-capability）。
     */
    public static final List<String> SIGNING_PATHS = List.of(
            "/api/auth/internal/task-capabilities",
            "/api/auth/internal/evaluation-worker-capabilities");

    /**
     * Task 创建/派发需要的执行档案入口。
     * 该接口由Task服务拉取Agent执行配置档案，走同一套机器认证。
     */
    public static final String EXECUTION_PROFILE_PATH = "/api/agent/internal/agents/*/execution-profile";

    // 工具常量类，禁止实例化
    private TaskInternalAuthConstant() {
    }
}
