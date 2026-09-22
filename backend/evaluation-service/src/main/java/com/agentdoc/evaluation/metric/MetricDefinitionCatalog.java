package com.agentdoc.evaluation.metric;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.enums.EvaluationMetricDirection;
import com.agentdoc.evaluation.enums.EvaluationMetricSource;
import com.agentdoc.evaluation.enums.EvaluationMetricValueType;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.evaluation.enums.EvaluationMetricDirection.HIGHER_IS_BETTER;
import static com.agentdoc.evaluation.enums.EvaluationMetricDirection.LOWER_IS_BETTER;
import static com.agentdoc.evaluation.enums.EvaluationMetricSource.EVALUATOR;
import static com.agentdoc.evaluation.enums.EvaluationMetricSource.EXECUTION;
import static com.agentdoc.evaluation.enums.EvaluationMetricValueType.BOOLEAN;
import static com.agentdoc.evaluation.enums.EvaluationMetricValueType.NUMBER;

/**
 * Phase 3 固定 Metric 目录。这里不是可编辑注册中心；既有 key 的语义不得改变。
 * <p>
 * 硬编码静态指标目录，存放Phase3全部内置指标定义；
 * 指标key与语义一旦发布不可变更，新增指标只能追加，存量指标禁止修改含义。
 * 分为两类：evaluator产出的评估指标、execution执行链路产出指标。
 * </p>
 */
public final class MetricDefinitionCatalog {
    /** ISO-4217币种标记，用于cost成本指标的单位标识 */
    public static final String ISO_4217_UNIT = "ISO-4217";

    /**
     * 全局指标定义只读Map，一次性初始化，不可修改。
     * evaluator()：评估器产出指标，绑定对应的evaluatorKey；
     * execution()：执行链路指标，不属于任何评估器，evaluatorKey=null；
     */
    private static final Map<String, MetricDefinition> DEFINITIONS = List.of(
            evaluator("evaluation.task-terminal.success", BOOLEAN, "boolean", HIGHER_IS_BETTER,
                    "task-terminal-status"),
            evaluator("evaluation.artifact-contract.valid", BOOLEAN, "boolean", HIGHER_IS_BETTER,
                    "artifact-contract"),
            evaluator("evaluation.text-assertion.pass-ratio", NUMBER, "ratio", HIGHER_IS_BETTER,
                    "text-assertion"),
            evaluator("evaluation.document-change.valid", BOOLEAN, "boolean", HIGHER_IS_BETTER,
                    "document-change-validator"),
            evaluator("evaluation.document-change.accuracy", NUMBER, "ratio", HIGHER_IS_BETTER,
                    "document-change-validator"),
            evaluator("evaluation.isolation-invariant.success", BOOLEAN, "boolean", HIGHER_IS_BETTER,
                    "isolation-invariant"),
            evaluator("evaluation.audit-ledger-integrity.success", BOOLEAN, "boolean", HIGHER_IS_BETTER,
                    "audit-ledger-integrity"),
            execution("execution.success", BOOLEAN, "boolean", HIGHER_IS_BETTER),
            execution("execution.input-tokens", NUMBER, "token", LOWER_IS_BETTER),
            execution("execution.cached-input-tokens", NUMBER, "token", LOWER_IS_BETTER),
            execution("execution.output-tokens", NUMBER, "token", LOWER_IS_BETTER),
            execution("execution.total-tokens", NUMBER, "token", LOWER_IS_BETTER),
            // 成本指标，开启currencyUnit，单位使用ISO-4217币种编码
            new MetricDefinition("execution.cost", NUMBER, ISO_4217_UNIT, true,
                    LOWER_IS_BETTER, EXECUTION, null),
            execution("execution.latency", NUMBER, "millisecond", LOWER_IS_BETTER),
            execution("execution.retry-count", NUMBER, "count", LOWER_IS_BETTER)
    ).stream().collect(Collectors.toUnmodifiableMap(MetricDefinition::metricKey, Function.identity()));

    // 私有构造，禁止实例化静态目录类
    private MetricDefinitionCatalog() {
    }

    /**
     * 根据metricKey获取指标定义，不存在则抛异常
     * @param metricKey 指标唯一key
     * @return 对应指标定义
     */
    public static MetricDefinition require(String metricKey) {
        MetricDefinition definition = DEFINITIONS.get(metricKey);
        if (definition == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未声明的标准 Metric key");
        }
        return definition;
    }

    /**
     * 返回全部指标定义列表，按metricKey字典序排序
     * @return 有序指标定义集合
     */
    public static List<MetricDefinition> definitions() {
        return DEFINITIONS.values().stream().sorted((left, right) ->
                left.metricKey().compareTo(right.metricKey())).toList();
    }

    /**
     * 构造【评估器类型】指标定义，绑定evaluatorKey
     * @param key metric唯一标识
     * @param type 值类型
     * @param unit 单位
     * @param direction 指标优化方向
     * @param evaluatorKey 关联的内置评估器key
     * @return MetricDefinition
     */
    private static MetricDefinition evaluator(String key, EvaluationMetricValueType type, String unit,
                                              EvaluationMetricDirection direction, String evaluatorKey) {
        return new MetricDefinition(key, type, unit, false, direction, EVALUATOR, evaluatorKey);
    }

    /**
     * 构造【执行链路类型】指标定义，无绑定evaluatorKey
     * @param key metric唯一标识
     * @param type 值类型
     * @param unit 单位
     * @param direction 指标优化方向
     * @return MetricDefinition
     */
    private static MetricDefinition execution(String key, EvaluationMetricValueType type, String unit,
                                              EvaluationMetricDirection direction) {
        return new MetricDefinition(key, type, unit, false, direction, EXECUTION, null);
    }
}
