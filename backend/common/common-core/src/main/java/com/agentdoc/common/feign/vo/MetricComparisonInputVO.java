package com.agentdoc.common.feign.vo;

import java.util.List;

/**
 * 可直接消费的标准 Metric 对比输入。
 * <p>
 * 批量指标比对的顶层入参，承载一组待横向对比的评估样本行，
 * 由Evaluation子服务组装后送入指标计算与对比算子。
 *
 * @param rows 待对比的指标样本行集合
 */
public record MetricComparisonInputVO(List<MetricComparisonRowVO> rows) {
}
