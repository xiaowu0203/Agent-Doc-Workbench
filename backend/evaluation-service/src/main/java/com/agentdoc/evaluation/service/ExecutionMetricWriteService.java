package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.EvaluationEvidenceBundleVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.evaluation.enums.EvaluationEvidenceType;
import com.agentdoc.evaluation.enums.EvaluationMetricDirection;
import com.agentdoc.evaluation.enums.EvaluationMetricSource;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricEvidenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricMapper;
import com.agentdoc.evaluation.metric.EvaluationMetricFactory;
import com.agentdoc.evaluation.metric.MetricWriteContext;
import com.agentdoc.evaluation.metric.StandardMetricValue;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationEvidenceReferenceEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEvidenceEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从权威执行事实生成一次且仅一次的执行型标准 Metric。
 * 接收Replay执行完成后的证据Bundle，生成执行指标，同时保存证据引用并建立指标-证据关联；
 * 幂等：同一caseAttempt只生成一次execution来源指标，重复调用直接返回已存在指标ID。
 */
@Service
@RequiredArgsConstructor
public class ExecutionMetricWriteService {

    private final EvaluationMetricMapper metricMapper;
    private final EvaluationEvidenceReferenceMapper evidenceMapper;
    private final EvaluationMetricEvidenceMapper metricEvidenceMapper;
    private final EvaluationCaseAttemptMapper attemptMapper;
    private final EvaluationCaseRunMapper caseRunMapper;

    /**
     * 写入本次CaseAttempt对应的执行指标与证据引用，幂等保证只生成一次
     * @param caseAttemptId 评估子尝试ID
     * @param facts 执行层返回的权威证据Bundle（task/execution/token账本）
     * @return 新增/已存在的metricId列表
     */
    @Transactional
    public List<Long> append(Long caseAttemptId, EvaluationEvidenceBundleVO facts) {
        EvaluationCaseAttemptEntity attempt = attemptMapper.selectById(caseAttemptId);
        EvaluationCaseRunEntity caseRun = attempt == null ? null : caseRunMapper.selectById(attempt.getCaseRunId());

        // 身份一致性校验：attempt、caseRun、taskId、runId、spaceId必须匹配
        if (attempt == null || caseRun == null || facts == null
                || !attempt.getReplayTaskId().equals(facts.taskId())
                || !attempt.getRunId().equals(caseRun.getRunId())
                || !attempt.getSpaceId().equals(caseRun.getSpaceId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "执行 Metric 事实与 CaseAttempt 身份不一致");
        }

        // 幂等校验：execution来源、producerId=attemptId的指标已存在则直接返回，不再重复生成
        List<EvaluationMetricEntity> existing = metricMapper.selectList(
                new LambdaQueryWrapper<EvaluationMetricEntity>()
                        .eq(EvaluationMetricEntity::getSource, EvaluationMetricSource.EXECUTION.name())
                        .eq(EvaluationMetricEntity::getProducerId, attempt.getId()));
        if (!existing.isEmpty()) {
            return existing.stream().map(EvaluationMetricEntity::getId).toList();
        }

        // 1. 创建证据引用记录
        Map<String, EvaluationEvidenceReferenceEntity> evidence = createEvidence(attempt, facts);
        // 2. 组装所有标准指标（execution.success、token、latency、retry-count、cost等）
        List<MetricWithEvidence> values = values(attempt, facts);

        // 指标写入上下文，用于工厂构建Metric实体
        MetricWriteContext context = new MetricWriteContext(attempt.getSpaceId(), attempt.getRunId(),
                caseRun.getId(), attempt.getId(), caseRun.getTestCaseVersionId(), null, null,
                attempt.getId(), null);
        List<EvaluationMetricEntity> metrics = values.stream()
                .map(value -> EvaluationMetricFactory.create(context, value.value())).toList();

        // 3. 入库：证据引用、指标、指标与证据关联表
        if (!evidence.isEmpty()) {
            evidenceMapper.insertBatch(List.copyOf(evidence.values()));
        }
        if (!metrics.isEmpty()) {
            metricMapper.insertBatch(metrics);
        }
        List<EvaluationMetricEvidenceEntity> links = new ArrayList<>();
        for (int index = 0; index < metrics.size(); index++) {
            for (String evidenceKey : values.get(index).evidenceKeys()) {
                EvaluationMetricEvidenceEntity link = new EvaluationMetricEvidenceEntity();
                link.setId(IdWorker.getId());
                link.setMetricId(metrics.get(index).getId());
                link.setEvidenceReferenceId(evidence.get(evidenceKey).getId());
                links.add(link);
            }
        }
        if (!links.isEmpty()) {
            metricEvidenceMapper.insertBatch(links);
        }
        return metrics.stream().map(EvaluationMetricEntity::getId).toList();
    }

    /**
     * 构造证据引用Map：task、agent执行、token账本三类证据
     */
    private Map<String, EvaluationEvidenceReferenceEntity> createEvidence(
            EvaluationCaseAttemptEntity attempt, EvaluationEvidenceBundleVO facts) {
        Map<String, EvaluationEvidenceReferenceEntity> result = new LinkedHashMap<>();
        result.put("task", evidence(attempt, EvaluationEvidenceType.TASK, String.valueOf(facts.taskId()),
                facts.taskStatus(), Map.of("status", facts.taskStatus())));
        if (facts.executionId() != null) {
            result.put("execution", evidence(attempt, EvaluationEvidenceType.AGENT_EXECUTION,
                    String.valueOf(facts.executionId()), facts.executionStatus(),
                    Map.of("taskId", facts.taskId())));
        }
        if (facts.inputTokens() != null || facts.outputTokens() != null || facts.cost() != null) {
            result.put("token", evidence(attempt, EvaluationEvidenceType.TOKEN_LEDGER,
                    String.valueOf(facts.executionId()), "TOKEN_LEDGER_PRESENT",
                    Map.of("taskId", facts.taskId())));
        }
        return result;
    }

    /**
     * 组装所有执行层标准指标
     * 指标清单：
     * 1. execution.success (bool)
     * 2. execution.input-tokens
     * 3. execution.cached-input-tokens
     * 4. execution.output-tokens
     * 5. execution.total-tokens
     * 6. execution.cost
     * 7. execution.latency 耗时(ms)
     * 8. execution.retry-count 重试次数
     */
    private List<MetricWithEvidence> values(EvaluationCaseAttemptEntity attempt,
                                            EvaluationEvidenceBundleVO facts) {
        List<MetricWithEvidence> values = new ArrayList<>();
        boolean success = "COMPLETED".equals(facts.taskStatus())
                && "COMPLETED".equals(facts.executionStatus());
        values.add(value(StandardMetricValue.bool("execution.success", success,
                EvaluationMetricDirection.HIGHER_IS_BETTER, EvaluationMetricSource.EXECUTION), "task"));

        addNumber(values, "execution.input-tokens", facts.inputTokens(), "token", "token");
        addNumber(values, "execution.cached-input-tokens", facts.cachedInputTokens(), "token", "token");
        addNumber(values, "execution.output-tokens", facts.outputTokens(), "token", "token");

        if (facts.inputTokens() != null && facts.outputTokens() != null) {
            addNumber(values, "execution.total-tokens", facts.inputTokens() + facts.outputTokens(),
                    "token", "token");
        }

        if (facts.cost() != null && facts.currency() != null) {
            values.add(value(StandardMetricValue.number("execution.cost", facts.cost(), facts.currency(),
                    EvaluationMetricDirection.LOWER_IS_BETTER, EvaluationMetricSource.EXECUTION), "token"));
        }

        if (facts.startedAt() != null && facts.finishedAt() != null
                && !facts.finishedAt().isBefore(facts.startedAt())) {
            addNumber(values, "execution.latency",
                    Duration.between(facts.startedAt(), facts.finishedAt()).toMillis(), "millisecond", "task");
        }

        // 重试次数 = attempt自身重试 + task侧上报重试
        long retryCount = Math.max(attempt.getAttemptNo() - 1L, 0L)
                + Math.max(facts.retryCount() == null ? 0L : facts.retryCount(), 0L);
        addNumber(values, "execution.retry-count", retryCount, "count", "task");

        return values;
    }

    /**
     * 辅助方法：非空数字指标加入列表
     */
    private void addNumber(List<MetricWithEvidence> values, String key, Long number,
                           String unit, String evidenceKey) {
        if (number != null) {
            values.add(value(StandardMetricValue.number(key, BigDecimal.valueOf(number), unit,
                    EvaluationMetricDirection.LOWER_IS_BETTER, EvaluationMetricSource.EXECUTION), evidenceKey));
        }
    }

    /**
     * 指标绑定关联证据key
     */
    private MetricWithEvidence value(StandardMetricValue value, String evidenceKey) {
        return new MetricWithEvidence(value, List.of(evidenceKey));
    }

    /**
     * 构造单条证据引用实体
     */
    private EvaluationEvidenceReferenceEntity evidence(EvaluationCaseAttemptEntity attempt,
                                                       EvaluationEvidenceType type, String businessId,
                                                       String summary, Map<String, ?> locator) {
        EvaluationEvidenceReferenceEntity entity = new EvaluationEvidenceReferenceEntity();
        entity.setId(IdWorker.getId());
        entity.setCaseAttemptId(attempt.getId());
        entity.setSpaceId(attempt.getSpaceId());
        entity.setEvidenceType(type.name());
        entity.setBusinessId(businessId);
        entity.setSummary(summary);
        entity.setLocatorJson(JsonUtils.toJson(locator));
        return entity;
    }

    /**
     * 指标值 + 关联证据key列表 记录
     */
    private record MetricWithEvidence(StandardMetricValue value, List<String> evidenceKeys) { }
}
