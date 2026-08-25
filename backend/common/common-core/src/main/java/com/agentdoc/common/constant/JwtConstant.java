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
    /** 声明键：Agent允许动作集合，逗号分隔，Task‑Capability JWT专属 */
    public static final String CLAIM_AGENT_ACTIONS = "agentActions";
    /** 声明键：主体类型；区分 HUMAN / AGENT */
    public static final String CLAIM_ACTOR_TYPE = "actorType";
    /** actorType: 主体类型‑Agent任务 */
    public static final String ACTOR_AGENT = "AGENT";

    // Agent允许动作常量
    // 阅读片段
    public static final String ACTION_READ_FRAGMENT = "READ_FRAGMENT";
    // 写入草稿
    public static final String ACTION_WRITE_DRAFT = "WRITE_DRAFT";
    // 创建修改请求
    public static final String ACTION_CREATE_CHANGE_REQUEST = "CREATE_CHANGE_REQUEST";

    // ===================== 通用HTTP Token头常量 =====================

    /** Authorization 头 Bearer 令牌类型 */
    public static final String TOKEN_TYPE_BEARER = "Bearer";
}
