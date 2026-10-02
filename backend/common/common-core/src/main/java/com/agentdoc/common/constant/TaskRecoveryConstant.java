package com.agentdoc.common.constant;

/** ADR-0006 恢复凭证的协议约束；不能由调用方覆盖。 */
public final class TaskRecoveryConstant {

    private TaskRecoveryConstant() { }

    /** 专用机器认证头。 */
    public static final String MACHINE_KEY_HEADER = "X-Task-Recovery-Machine-Key";
    /** 专用恢复凭证头，不进入普通身份上下文。 */
    public static final String CAPABILITY_HEADER = "X-Task-Recovery-Capability";
    /** 唯一协调服务。 */
    public static final String SERVICE = "task-service";
    /** A2A 恢复受众。 */
    public static final String A2A_AUDIENCE = "agent-service-task-recovery";
    /** 草稿收尾受众。 */
    public static final String DRAFT_AUDIENCE = "document-service-task-finalization";
    /** A2A 恢复用途。 */
    public static final String A2A_PURPOSE = "TASK_TERMINAL_RECOVERY";
    /** 草稿收尾用途。 */
    public static final String DRAFT_PURPOSE = "TASK_DRAFT_FINALIZATION";
    /** 查询既有远端任务。 */
    public static final String QUERY = "QUERY_EXISTING_A2A_TASK";
    /** 取消已记录取消意图的远端任务。 */
    public static final String CANCEL = "CANCEL_EXISTING_A2A_TASK";
    /** 提交既有草稿暂存。 */
    public static final String FINALIZE = "FINALIZE_EXISTING_TASK_DRAFT";
    /** 丢弃既有草稿暂存。 */
    public static final String DISCARD = "DISCARD_EXISTING_TASK_DRAFT";
    /** 用途声明。 */
    public static final String PURPOSE = "purpose";
    /** 独立动作声明。 */
    public static final String ACTIONS = "recoveryActions";
    /** 已存在的 A2A 任务声明。 */
    public static final String A2A_TASK_ID = "a2aTaskId";
    /** 原证明身份。 */
    public static final String SOURCE_JTI = "sourceCapabilityJti";
    /** 本次恢复关联身份。 */
    public static final String RECOVERY_ID = "recoveryId";
    /** 受控查询获得的远端终态。 */
    public static final String REMOTE_STATUS = "remoteTerminalStatus";
    /** 最长查询/取消凭证有效期，秒。 */
    public static final long A2A_TTL_SECONDS = 300;
    /** 最长草稿收尾凭证有效期，秒。 */
    public static final long DRAFT_TTL_SECONDS = 60;
    /** 机器密钥最小 UTF-8 字节数。 */
    public static final int MIN_MACHINE_KEY_BYTES = 32;
    /** 历史证明最大字符数，避免无界解析。 */
    public static final int MAX_PROOF_LENGTH = 16_384;
    /** 数据库既有 A2A 身份字段上限。 */
    public static final int MAX_A2A_ID_LENGTH = 255;
    /** 单次连接的最大等待时间，毫秒。 */
    public static final int MAX_CONNECT_TIMEOUT_MS = 2_000;
    /** 单次响应的最大等待时间，毫秒。 */
    public static final int MAX_READ_TIMEOUT_MS = 5_000;
    /** audit_log 的服务主体保留 ID；真实身份另以 service 记录，不冒用用户。 */
    public static final long SERVICE_ACTOR_ID = 0;
    /** 整轮恢复的最长处理预算，必须小于 55 秒对账锁租期。 */
    public static final long PROCESS_BUDGET_SECONDS = 45;
    /** 第一/阈值告警的小时汇总窗口。 */
    public static final long ALERT_WINDOW_SECONDS = 3600;
    /** 连续失败告警阈值。 */
    public static final int FAILURE_ALERT_THRESHOLD = 3;
    /** 告警去重键，仅存非敏感身份。 */
    public static final String ALERT_KEY_PREFIX = "task:recovery:alert:";
}
