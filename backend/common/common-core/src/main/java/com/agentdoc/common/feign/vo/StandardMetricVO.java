package com.agentdoc.common.feign.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 可聚合、可比较的标准业务 Metric。
 * <p>
 * 评估产出的标准化指标记录，统一存储不同Evaluator输出结果；
 * 按类型存放数值、布尔或字符串值，附带证据引用，支持跨运行横向对比与指标聚合。
 *
 * @param id                  指标主键ID
 * @param spaceId             工作空间ID，权限隔离边界
 * @param runId               评估运行实例ID
 * @param caseRunId           用例运行ID
 * @param caseAttemptId       用例单次尝试ID
 * @param testCaseVersionId   测试用例版本ID
 * @param evaluationResultId  评估结果记录ID
 * @param evaluatorVersionId  Evaluator算子版本ID
 * @param contractVersion     指标契约版本，保证定义解析向前兼容
 * @param source              指标来源，区分内置评估器或自定义算子
 * @param producerId          指标生成者标识
 * @param metricKey           指标唯一标识Key
 * @param valueType           值类型：numeric / boolean / string
 * @param numericValue        数值类型指标值，非数值时为null
 * @param booleanValue        布尔类型指标值，非布尔时为null
 * @param stringValue         字符串类型指标值，非字符串时为null
 * @param unit                计量单位，无单位时为null
 * @param direction           优化方向：越高越好/越低越好/贴近目标等
 * @param createdAt           指标生成时间
 * @param evidence            指标关联证据引用列表，指向审计与回放依据
 */
public record StandardMetricVO(
        Long id,
        Long spaceId,
        Long runId,
        Long caseRunId,
        Long caseAttemptId,
        Long testCaseVersionId,
        Long evaluationResultId,
        Long evaluatorVersionId,
        Integer contractVersion,
        String source,
        Long producerId,
        String metricKey,
        String valueType,
        BigDecimal numericValue,
        Boolean booleanValue,
        String stringValue,
        String unit,
        String direction,
        LocalDateTime createdAt,
        List<MetricEvidenceReferenceVO> evidence) {
}
