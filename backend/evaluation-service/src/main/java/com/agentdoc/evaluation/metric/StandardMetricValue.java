package com.agentdoc.evaluation.metric;

import com.agentdoc.evaluation.enums.EvaluationMetricDirection;
import com.agentdoc.evaluation.enums.EvaluationMetricSource;
import com.agentdoc.evaluation.enums.EvaluationMetricValueType;

import java.math.BigDecimal;

/**
 * Evaluator 或执行事实适配器提交给统一写入层的标准 Metric 值。
 * <p>
 * 标准化指标值载体，统一封装三类指标（数字/布尔/字符串）；
 * 静态工厂方法简化构造，契约校验层会保证有且仅有一个值字段被填充。
 * </p>
 * @param metricKey 指标唯一key，与MetricDefinitionCatalog中定义对齐
 * @param valueType 指标值类型枚举：NUMBER / BOOLEAN / STRING
 * @param numericValue 数值类型指标值，valueType=NUMBER时有效，其余场景为null
 * @param booleanValue 布尔类型指标值，valueType=BOOLEAN时有效，其余场景为null
 * @param stringValue 字符串类型指标值，valueType=STRING时有效，其余场景为null
 * @param unit 指标单位，如 token、millisecond、ratio、ISO-4217
 * @param direction 指标优化方向：越大越好 / 越小越好
 * @param source 指标来源：EVALUATOR评估器产出 / EXECUTION执行链路产出
 */
public record StandardMetricValue(
        String metricKey,
        EvaluationMetricValueType valueType,
        BigDecimal numericValue,
        Boolean booleanValue,
        String stringValue,
        String unit,
        EvaluationMetricDirection direction,
        EvaluationMetricSource source) {

    /**
     * 构造 NUMBER 类型标准指标
     * @param metricKey 指标key
     * @param value 数值
     * @param unit 单位
     * @param direction 优化方向
     * @param source 指标来源
     * @return StandardMetricValue
     */
    public static StandardMetricValue number(String metricKey, BigDecimal value, String unit,
                                             EvaluationMetricDirection direction,
                                             EvaluationMetricSource source) {
        return new StandardMetricValue(metricKey, EvaluationMetricValueType.NUMBER, value, null, null,
                unit, direction, source);
    }

    /**
     * 构造 BOOLEAN 类型标准指标，unit固定为boolean
     * @param metricKey 指标key
     * @param value 布尔值
     * @param direction 优化方向
     * @param source 指标来源
     * @return StandardMetricValue
     */
    public static StandardMetricValue bool(String metricKey, boolean value,
                                           EvaluationMetricDirection direction,
                                           EvaluationMetricSource source) {
        return new StandardMetricValue(metricKey, EvaluationMetricValueType.BOOLEAN, null, value, null,
                "boolean", direction, source);
    }

    /**
     * 构造 STRING 类型标准指标
     * @param metricKey 指标key
     * @param value 字符串值
     * @param unit 单位
     * @param direction 优化方向
     * @param source 指标来源
     * @return StandardMetricValue
     */
    public static StandardMetricValue string(String metricKey, String value, String unit,
                                             EvaluationMetricDirection direction,
                                             EvaluationMetricSource source) {
        return new StandardMetricValue(metricKey, EvaluationMetricValueType.STRING, null, null, value,
                unit, direction, source);
    }
}
