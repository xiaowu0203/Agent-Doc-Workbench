package com.agentdoc.evaluation.evaluator;

import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import com.agentdoc.evaluation.metric.EvidenceReferenceValue;
import com.agentdoc.evaluation.metric.StandardMetricOutput;

import java.util.List;

/**
 * 内置确定性 Evaluator 的纯计算结果。
 * <p>
 * 存放评估器执行产出的判定状态、摘要编码、详情、指标与证据引用；
 * 属于纯结果数据载体，不含业务写逻辑。
 * </p>
 */
public record DeterministicEvaluationOutcome(
        /* 评估判定结论状态 */
        EvaluationResultStatus status,
        /* 结果摘要编码，用于归类该评估结论类型 */
        String summaryCode,
        /* 结构化详情JSON字符串 */
        String detailsJson,
        /* 本次评估产出的指标列表 */
        List<StandardMetricOutput> metrics,
        /* 评估引用的证据来源列表 */
        List<EvidenceReferenceValue> evidence) { }
