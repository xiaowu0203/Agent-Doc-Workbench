package com.agentdoc.evaluation.metric;

/**
 * Metric 写入时已由 Evaluation Core 解析并对账的业务身份。
 * <p>
 * 承载指标写入所需的全套业务归属上下文；
 * 所有字段在进入MetricFactory前完成身份校验，用于区分评估型/执行型指标生产者。
 * </p>
 * @param spaceId 空间ID
 * @param runId 任务执行ID
 * @param caseRunId 用例执行ID
 * @param caseAttemptId 用例重试尝试ID
 * @param testCaseVersionId 测试用例版本ID
 * @param evaluationResultId 评估结果ID，评估型指标才有值
 * @param evaluatorVersionId 评估器版本ID，评估型指标才有值
 * @param producerId 生产者ID，评估型=evaluationResultId，执行型=caseAttemptId
 * @param evaluatorKey 评估器标识，评估型指标绑定对应evaluatorKey，执行型为null
 */
public record MetricWriteContext(
        Long spaceId,
        Long runId,
        Long caseRunId,
        Long caseAttemptId,
        Long testCaseVersionId,
        Long evaluationResultId,
        Long evaluatorVersionId,
        Long producerId,
        String evaluatorKey) {
}
