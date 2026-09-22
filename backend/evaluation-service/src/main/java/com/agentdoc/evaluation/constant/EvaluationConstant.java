package com.agentdoc.evaluation.constant;

import java.util.Set;

/**
 * Evaluation 领域固定契约常量类。
 * <p>
 * 存放评估模块不可配置的硬编码常量、版本定义、上限阈值、内置评估器列表，
 * 不随配置文件变更，属于领域层固定约束。
 * </p>
 */
public final class EvaluationConstant {

    /**
     * 内容哈希结构协议版本，用于哈希序列化/反序列化兼容
     */
    public static final int CONTENT_HASH_SCHEMA_VERSION = 1;

    /**
     * 评估指标契约版本号，用于指标结构、计算逻辑版本兼容
     */
    public static final int METRIC_CONTRACT_VERSION = 1;

    /**
     * 数据集最大用例数量上限，限制单次评估数据集规模
     */
    public static final int MAX_DATASET_CASE_COUNT = 100;

    /**
     * 指标查询时允许传入的指标ID最大数量上限
     */
    public static final int MAX_METRIC_QUERY_IDS = 100;

    /**
     * 指标查询时允许传入的查询Key最大数量上限
     */
    public static final int MAX_METRIC_QUERY_KEYS = 100;

    /**
     * 指标查询返回结果行最大上限，用于防止查询返回超大结果集
     */
    public static final int MAX_METRIC_QUERY_ROWS = 10001;

    /**
     * 单次对比评估最大运行次数上限
     */
    public static final int MAX_COMPARISON_RUN_COUNT = 20;

    /**
     * 系统内置评估器名称集合，平台原生支持的评估规则
     */
    public static final Set<String> BUILT_IN_EVALUATORS = Set.of(
            "task-terminal-status", "artifact-contract", "text-assertion",
            "document-change-validator", "isolation-invariant", "audit-ledger-integrity");

    /**
     * 私有构造，禁止实例化常量类
     */
    private EvaluationConstant() { }
}
