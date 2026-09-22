package com.agentdoc.evaluation.enums;

/**
 * 标准 Metric 值类型枚举；持久化时只允许对应的一个 typed value 非空。
 * <p>
 * 约束指标存储的数据类型，一条指标记录仅可使用对应类型字段存储值，其余类型字段置空。
 * </p>
 */
public enum EvaluationMetricValueType {
    /** 数值类型指标 */
    NUMBER,
    /** 布尔类型指标 */
    BOOLEAN,
    /** 字符串类型指标 */
    STRING
}
