package com.agentdoc.evaluation.metric;

import com.agentdoc.evaluation.enums.EvaluationMetricSource;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 标准 Metric 的当前有效投影规则。
 * <p>
 * 指标投影筛选工具类，用于从原始指标列表中过滤出**当前生效版本**的指标；
 * 规则：仅保留用例当前重试attempt、且为对应评估器版本下最新评估结果产出的指标。
 * </p>
 */
public final class MetricEffectiveProjection {
    // 私有构造，禁止实例化
    private MetricEffectiveProjection() { }

    /**
     * 执行指标投影筛选，返回有效指标列表
     *
     * @param metrics 原始全量指标实体列表
     * @param caseRuns 用例执行记录，用于获取每个caseRun对应的当前attemptId
     * @param results 评估结果列表，用于按(caseAttemptId,evaluatorVersionId)取最新评估结果
     * @return 过滤后生效的EvaluationMetricEntity集合
     */
    public static List<EvaluationMetricEntity> select(
            List<EvaluationMetricEntity> metrics,
            List<EvaluationCaseRunEntity> caseRuns,
            List<EvaluationResultEntity> results) {
        // 构建caseRunId -> 当前caseAttemptId映射，拿到各用例执行的最新重试尝试
        Map<Long, Long> currentAttemptByCaseRun = new HashMap<>();
        for (EvaluationCaseRunEntity caseRun : caseRuns) {
            currentAttemptByCaseRun.put(caseRun.getId(), caseRun.getCurrentAttemptId());
        }

        // 按(caseAttemptId, evaluatorVersionId)分组，保留每组最新评估结果
        Map<ResultKey, EvaluationResultEntity> latestResult = new HashMap<>();
        for (EvaluationResultEntity result : results) {
            ResultKey key = new ResultKey(result.getCaseAttemptId(), result.getEvaluatorVersionId());
            latestResult.merge(key, result, MetricEffectiveProjection::latest);
        }

        return metrics.stream()
                // 过滤：指标所属caseAttempt必须是该caseRun的当前生效attempt
                .filter(metric -> metric.getCaseAttemptId().equals(currentAttemptByCaseRun.get(metric.getCaseRunId())))
                // 过滤：校验指标生产者是否为当前最新评估结果
                .filter(metric -> isCurrentProducer(metric, latestResult))
                .toList();
    }

    /**
     * 判断指标生产者是否为当前最新评估结果
     * @param metric 待校验指标实体
     * @param latestResult 分组后的最新评估结果Map
     * @return true=该指标为当前有效生产者产出
     */
    private static boolean isCurrentProducer(
            EvaluationMetricEntity metric,
            Map<ResultKey, EvaluationResultEntity> latestResult) {
        // 执行型指标：直接有效，无需匹配评估结果
        if (EvaluationMetricSource.EXECUTION.name().equals(metric.getSource())) {
            return true;
        }
        // 评估型指标：需要匹配到该(caseAttempt,evaluatorVersion)下最新评估结果ID
        EvaluationResultEntity latest = latestResult.get(
                new ResultKey(metric.getCaseAttemptId(), metric.getEvaluatorVersionId()));
        return latest != null && latest.getId().equals(metric.getEvaluationResultId());
    }

    /**
     * 取两个评估结果中更新的那一条；优先按评估重试号evaluationAttemptNo，相同则按主键ID
     * @param left 评估结果A
     * @param right 评估结果B
     * @return 较新的EvaluationResultEntity
     */
    private static EvaluationResultEntity latest(EvaluationResultEntity left, EvaluationResultEntity right) {
        Comparator<EvaluationResultEntity> comparator = Comparator
                .comparing(EvaluationResultEntity::getEvaluationAttemptNo)
                .thenComparing(EvaluationResultEntity::getId);
        return comparator.compare(left, right) >= 0 ? left : right;
    }

    /**
     * 分组key：用例尝试ID + 评估器版本ID，唯一标识一组评估结果
     * @param caseAttemptId 用例重试尝试ID
     * @param evaluatorVersionId 评估器版本ID
     */
    private record ResultKey(Long caseAttemptId, Long evaluatorVersionId) { }
}
