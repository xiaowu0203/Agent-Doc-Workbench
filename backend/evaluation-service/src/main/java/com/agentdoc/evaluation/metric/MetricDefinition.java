package com.agentdoc.evaluation.metric;

import com.agentdoc.evaluation.enums.EvaluationMetricDirection;
import com.agentdoc.evaluation.enums.EvaluationMetricSource;
import com.agentdoc.evaluation.enums.EvaluationMetricValueType;

/**
 * 代码内固定的 Metric 语义定义。
 *
 * @param metricKey 稳定技术标识
 * @param valueType 值类型
 * @param unit 固定单位；currencyUnit=true 时固定为 ISO-4217 契约占位符
 * @param currencyUnit 是否要求实际值使用 ISO-4217 币种单位
 * @param direction 比较方向
 * @param source 权威来源
 * @param evaluatorKey 评估型 Metric 的唯一生产者；执行型为空
 */
public record MetricDefinition(
        String metricKey,
        EvaluationMetricValueType valueType,
        String unit,
        boolean currencyUnit,
        EvaluationMetricDirection direction,
        EvaluationMetricSource source,
        String evaluatorKey) {
}
