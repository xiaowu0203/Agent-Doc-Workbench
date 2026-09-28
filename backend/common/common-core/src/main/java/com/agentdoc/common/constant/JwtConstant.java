package com.agentdoc.common.constant;

/**
 * JWT Payload 声明键常量：auth-service（签发）与网关/业务服务（解析）共用，
 * 避免 claim 键名、scope 等魔法字符串在签发方与解析方各自硬编码导致拼写漂移。
 */
public final class JwtConstant {

    private JwtConstant() {
    }

    // ===================== 普通用户登录JWT 相关 =====================

    /** 声明键：用户名 */
    public static final String CLAIM_USERNAME = "username";

    /** 声明键：用户昵称 */
    public static final String CLAIM_NICKNAME = "nickname";

    /** 声明键：权限作用域（逗号分隔） */
    public static final String CLAIM_SCOPE = "scope";

    /** 默认用户权限作用域 */
    public static final String SCOPE_USER = "user";

    /** 声明键：平台角色标识符列表。 */
    public static final String CLAIM_PLATFORM_ROLES = "platformRoles";

    // ===================== Task‑Capability 任务短时能力JWT 专用 =====================

    /** 声明键：Agent ID（外部 Agent 访问场景） */
    public static final String CLAIM_AGENT_ID = "agentId";
    /** 任务能力令牌权限作用域 */
    public static final String SCOPE_AGENT = "agent";
    /** 声明键：任务ID，Task‑Capability JWT专属 */
    public static final String CLAIM_TASK_ID = "taskId";
    /** 声明键：空间ID，Task‑Capability JWT专属 */
    public static final String CLAIM_SPACE_ID = "spaceId";
    /** 声明键：文档ID，Task‑Capability JWT专属 */
    public static final String CLAIM_DOCUMENT_ID = "documentId";
    /** 声明键：任务执行模式。 */
    public static final String CLAIM_EXECUTION_MODE = "executionMode";
    /** 声明键：冻结文档版本。 */
    public static final String CLAIM_DOCUMENT_VERSION_SNAPSHOT = "documentVersionSnapshot";
    /** 声明键：冻结文档正文 SHA-256。 */
    public static final String CLAIM_DOCUMENT_CONTENT_SHA256 = "documentContentSha256";
    /** 声明键：输入快照 schema 版本。 */
    public static final String CLAIM_INPUT_SNAPSHOT_SCHEMA_VERSION = "inputSnapshotSchemaVersion";
    /** 声明键：输入快照 hash。 */
    public static final String CLAIM_INPUT_SNAPSHOT_HASH = "inputSnapshotHash";
    /** 声明键：派生请求 hash；原始 LIVE Task 为空。 */
    public static final String CLAIM_DERIVATION_REQUEST_HASH = "derivationRequestHash";
    /** Task Capability 固定 audience。 */
    public static final String TASK_CAPABILITY_AUDIENCE = "workbench-task-capability";
    /** 声明键：Agent允许动作集合，逗号分隔，Task‑Capability JWT专属 */
    public static final String CLAIM_AGENT_ACTIONS = "agentActions";
    /** 声明键：主体类型；区分 HUMAN / AGENT / SERVICE */
    public static final String CLAIM_ACTOR_TYPE = "actorType";
    /** actorType: 主体类型‑Agent任务 */
    public static final String ACTOR_AGENT = "AGENT";

    // ===================== Evaluation Worker Capability 专用 =====================

    /** Evaluation Worker 能力令牌权限作用域。 */
    public static final String SCOPE_SERVICE = "service";
    /** actorType: 后台服务。 */
    public static final String ACTOR_SERVICE = "SERVICE";
    /** WorkerCapability 固定服务身份。 */
    public static final String EVALUATION_SERVICE = "evaluation-service";
    /** WorkerCapability 固定 audience。 */
    public static final String EVALUATION_WORKER_CAPABILITY_AUDIENCE = "task-service-internal";
    /** 声明键：服务身份。 */
    public static final String CLAIM_SERVICE = "service";
    /** 声明键：EvaluationRun ID。 */
    public static final String CLAIM_RUN_ID = "runId";
    /** 声明键：排序后 Task ID 集合的稳定哈希。 */
    public static final String CLAIM_TASK_IDS_HASH = "taskIdsHash";
    /** 声明键：Worker 允许动作集合。 */
    public static final String CLAIM_WORKER_ACTIONS = "workerActions";
    /** 批量查询本 Run Replay 状态。 */
    public static final String ACTION_BATCH_READ_TASK_STATUS = "BATCH_READ_TASK_STATUS";
    /** 查询本 Run 的评估证据投影。 */
    public static final String ACTION_READ_EVALUATION_EVIDENCE = "READ_EVALUATION_EVIDENCE";
    /** 读取并校验候选文档变更；payload 不得返回调用方。 */
    public static final String ACTION_VALIDATE_DOCUMENT_CHANGE = "VALIDATE_DOCUMENT_CHANGE";
    /** 终止本 Run 绑定的 Replay。 */
    public static final String ACTION_CANCEL_RUN_TASKS = "CANCEL_RUN_TASKS";

    // Agent允许动作常量
    // 阅读片段
    public static final String ACTION_READ_FRAGMENT = "READ_FRAGMENT";
    // 写入草稿
    public static final String ACTION_WRITE_DRAFT = "WRITE_DRAFT";
    // 创建修改请求
    public static final String ACTION_CREATE_CHANGE_REQUEST = "CREATE_CHANGE_REQUEST";
    /** 捕获隔离执行候选产物。 */
    public static final String ACTION_CAPTURE_EXECUTION_ARTIFACT = "CAPTURE_EXECUTION_ARTIFACT";

    // ===================== 通用HTTP Token头常量 =====================

    /** Authorization 头 Bearer 令牌类型 */
    public static final String TOKEN_TYPE_BEARER = "Bearer";
}
