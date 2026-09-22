package com.agentdoc.evaluation.metric;

import com.agentdoc.evaluation.enums.EvaluationResultStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 内置 Evaluator 提交 Result、Metric 和 Evidence 的原子写入命令。
 * <p>
 * 记录类，承载评估执行完成后的全部输出数据，用于一次性提交评估结果、指标、证据引用；
 * 作为入参传给写入服务，保证评估结果、指标、证据在同一业务上下文内关联。
 * </p>
 * @param spaceId 空间ID
 * @param runId 任务执行ID
 * @param caseRunId 用例执行ID
 * @param caseAttemptId 用例重试尝试ID
 * @param testCaseVersionId 测试用例版本ID
 * @param evaluatorVersionId 评估器版本ID
 * @param evaluationAttemptNo 评估重试次数序号
 * @param status 评估结果状态（成功/失败等）
 * @param summaryCode 评估结果摘要编码，用于快速归类结果
 * @param detailsJson 评估详情JSON文本
 * @param traceId 链路追踪traceId
 * @param spanId 链路追踪spanId
 * @param startedAt 评估开始时间
 * @param finishedAt 评估结束时间
 * @param metrics 评估产出指标列表
 * @param evidence 评估证据引用列表
 */
public record EvaluatorResultWriteCommand(
        Long spaceId,
        Long runId,
        Long caseRunId,
        Long caseAttemptId,
        Long testCaseVersionId,
        Long evaluatorVersionId,
        Integer evaluationAttemptNo,
        EvaluationResultStatus status,
        String summaryCode,
        String detailsJson,
        String traceId,
        String spanId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<StandardMetricOutput> metrics,
        List<EvidenceReferenceValue> evidence) {
}
