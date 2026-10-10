package com.agentdoc.common.enums;

/** schema 2 的稳定线上拒绝原因；不替代 Result 业务 code。 */
public enum OnlineReasonCode {
    /** 请求幂等键缺失或非法。 */
    REQUEST_KEY_REQUIRED,
    /** 同身份请求载荷冲突。 */
    IDEMPOTENCY_CONFLICT,
    /** 需要实际空间所有者。 */
    OWNER_REQUIRED,
    /** 当前授权人的动作权限不足。 */ RESOURCE_FORBIDDEN,
    /** 权威线上身份或 accepted binding 不匹配。 */ BINDING_INVALID,
    /** 自动保护窄授权缺失、过期或已撤权。 */ CANCEL_AUTHORIZATION_UNAVAILABLE,
    /** 十进制身份或预算非法。 */
    ID_INVALID,
    /** 随机种子非法。 */
    SEED_INVALID,
    /** 分桶参数非法。 */
    BUCKET_INVALID,
    /** Unicode 编码非法。 */
    UNICODE_INVALID,
    /** 不支持旧线上协议。 */
    UNSUPPORTED_ONLINE_SCHEMA,
    /** 冻结清单或请求非法。 */
    MANIFEST_INVALID,
    /** 清单超过字节上限。 */
    MANIFEST_TOO_LARGE,
    /** 文档范围非法。 */
    SCOPE_INVALID,
    /** 冻结期望映射不完整。 */
    EXPECTED_MAPPING_MISSING,
    /** 缺少原始质量主规则。 */
    PRIMARY_QUALITY_REQUIRED,
    /** 线上规则不符合发布契约。 */
    ONLINE_RULE_INVALID,
    /** 规则版本或 LIVE 引擎未就绪。 */
    ONLINE_RULE_NOT_READY,
    /** 旧规则不适用于 LIVE 原始评价。 */
    ONLINE_RULE_NOT_LIVE_COMPATIBLE,
    /** 线上规则不能用于离线证据。 */
    ONLINE_RULE_NOT_OFFLINE_COMPATIBLE,
    /** 线上执行保护链路未就绪。 */
    ONLINE_EXECUTION_NOT_READY,
    /** 线上报告未就绪。 */
    ONLINE_REPORT_NOT_READY,
    /** 需要实际保留与容量确认。 */
    RETENTION_CONFIRMATION_REQUIRED,
    /** 历史估算尚不可用。 */
    HISTORICAL_ESTIMATE_UNAVAILABLE,
    /** 空间运行占位被占用。 */
    SPACE_SLOT_OCCUPIED,
    /** 当前依赖与冻结值漂移。 */
    DEPENDENCY_DRIFT,
    /** 依赖查证不可用。 */
    DEPENDENCY_UNAVAILABLE,
    /** 当前范围与冻结范围漂移。 */
    SCOPE_DRIFT,
    /** 双模板身份或内容证明非法。 */
    TEMPLATE_INVALID,
    /** 评价主体混用或不完整。 */
    EVALUATION_SUBJECT_INVALID,
    /** SRM 策略样本不足。 */
    SRM_NOT_ENOUGH_UNITS,
    /** SRM 分配诊断异常。 */
    SRM_DETECTED,
    /** SRM 参数非法。 */
    SRM_INVALID,
    /** 范围未覆盖两组。 */
    RANGE_MISSING_VARIANT,
    /** 线上实验不存在。 */
    ONLINE_EXPERIMENT_NOT_FOUND,
    /** 已关闭新分配门禁。 */ ONLINE_GATE_CLOSED,
    /** 达到任务数或Token预留上限。 */ ONLINE_BUDGET_EXHAUSTED,
    /** 同组已有执行，等待而非执行失败。 */ ONLINE_SLOT_BUSY,
    /** 已释放或不匹配的槽证明。 */ ONLINE_SLOT_INVALID,
    /** 实际Token超出授权，紧急停止。 */ ONLINE_TOKEN_OVERRUN,
    /** 执行或账本事实无法查证。 */ ONLINE_FACT_UNKNOWN,
    /** 同组最近20项实际终态中失败至少6项。 */ ONLINE_HEALTH_FAILURE,
    /** 时间窗口关闭。 */ ONLINE_ASSIGNMENT_DEADLINE,
    /** 状态版本不匹配。 */ ONLINE_STATE_CONFLICT
}
