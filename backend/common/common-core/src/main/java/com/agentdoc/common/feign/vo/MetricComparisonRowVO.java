package com.agentdoc.common.feign.vo;

import java.util.List;

/**
 * 按 TestCaseVersion 与 metricKey 对齐的一行对比输入。
 * <p>
 * 指标比对的单行记录，绑定测试用例版本与指标标识，携带指标契约元信息与多组观测值，
 * 用于对同一指标在不同运行/回放结果之间做横向对比。
 *
 * @param testCaseVersionId 测试用例版本ID
 * @param metricKey         指标唯一键
 * @param contractVersion   指标契约版本，保证指标定义解析兼容
 * @param valueType         指标值类型，如数值、布尔、枚举等
 * @param unit              计量单位，无单位时为null
 * @param direction         指标优化方向：越高越好/越低越好/越接近目标越好
 * @param source            指标来源，标识是内置Evaluator还是自定义算子
 * @param values            多组待对比的指标观测值集合
 */
public record MetricComparisonRowVO(
        Long testCaseVersionId,
        String metricKey,
        Integer contractVersion,
        String valueType,
        String unit,
        String direction,
        String source,
        List<MetricComparisonValueVO> values) {
}
