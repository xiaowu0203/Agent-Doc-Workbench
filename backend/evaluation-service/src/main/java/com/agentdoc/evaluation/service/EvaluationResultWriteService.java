package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.evaluation.enums.EvaluationAttemptStatus;
import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import com.agentdoc.evaluation.enums.EvaluationVersionStatus;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricEvidenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.metric.EvaluationMetricFactory;
import com.agentdoc.evaluation.metric.EvaluatorResultWriteCommand;
import com.agentdoc.evaluation.metric.EvidenceReferenceValue;
import com.agentdoc.evaluation.metric.MetricWriteContext;
import com.agentdoc.evaluation.metric.StandardMetricOutput;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationEvidenceReferenceEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEvidenceEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.agentdoc.evaluation.pojo.vo.EvaluationResultWriteVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EvaluationResult、StandardMetric 与 EvidenceReference 的唯一原子写入边界。
 * 事务内一次性写入评估结果、指标、证据引用以及指标-证据关联；
 * 前置强校验身份归属、状态约束、字段长度与JSON合法性，保证数据一致性。
 */
@Service
@RequiredArgsConstructor
public class EvaluationResultWriteService {

    /**
     * summaryCode 最大字符长度
     */
    private static final int MAX_SUMMARY_CODE_LENGTH = 64;
    /**
     * 证据业务ID最大字符长度
     */
    private static final int MAX_EVIDENCE_BUSINESS_ID_LENGTH = 191;
    /**
     * 证据摘要最大字符长度
     */
    private static final int MAX_EVIDENCE_SUMMARY_LENGTH = 1000;

    private final EvaluationResultMapper resultMapper;
    private final EvaluationMetricMapper metricMapper;
    private final EvaluationEvidenceReferenceMapper evidenceMapper;
    private final EvaluationMetricEvidenceMapper metricEvidenceMapper;
    private final EvaluationCaseAttemptMapper attemptMapper;
    private final EvaluationCaseRunMapper caseRunMapper;
    private final EvaluatorVersionMapper evaluatorVersionMapper;

    /**
     * 追加写入一轮评估结果
     * 事务原子写入评估主记录、证据、指标、指标证据关联；
     * 前置身份校验、命令参数校验，构建各实体并落库，返回写入结果VO
     */
    @Transactional
    public EvaluationResultWriteVO append(EvaluatorResultWriteCommand command) {
        WriteIdentity identity = requireIdentity(command);
        validateCommand(command);
        EvaluationResultEntity result = result(command, identity.evaluatorVersion());
        Map<String, EvaluationEvidenceReferenceEntity> evidence = buildEvidence(command, result.getId());
        List<EvaluationMetricEntity> metrics = buildMetrics(command, identity, result.getId(), evidence.keySet());

        resultMapper.insert(result);
        evidence.values().forEach(evidenceMapper::insert);
        metrics.forEach(metricMapper::insert);
        Map<String, Long> evidenceIds = new HashMap<>();
        evidence.forEach((key, value) -> evidenceIds.put(key, value.getId()));
        for (int index = 0; index < metrics.size(); index++) {
            EvaluationMetricEntity metric = metrics.get(index);
            StandardMetricOutput output = command.metrics().get(index);
            for (String evidenceKey : safeList(output.evidenceReferenceKeys())) {
                EvaluationMetricEvidenceEntity link = new EvaluationMetricEvidenceEntity();
                link.setId(IdWorker.getId());
                link.setMetricId(metric.getId());
                link.setEvidenceReferenceId(evidenceIds.get(evidenceKey));
                metricEvidenceMapper.insert(link);
            }
        }
        return new EvaluationResultWriteVO(result.getId(), result.getStatus(),
                metrics.stream().map(EvaluationMetricEntity::getId).toList(),
                evidence.values().stream().map(EvaluationEvidenceReferenceEntity::getId).toList());
    }

    /**
     * 校验写入身份与归属关系
     * 校验Attempt、CaseRun、EvaluatorVersion存在性，校验跨表外键一致性；
     * 校验评估器版本已发布，且当前CaseAttempt处于EVALUATING状态允许写入
     */
    private WriteIdentity requireIdentity(EvaluatorResultWriteCommand command) {
        if (command == null || command.spaceId() == null || command.runId() == null
                || command.caseRunId() == null || command.caseAttemptId() == null
                || command.testCaseVersionId() == null || command.evaluatorVersionId() == null) {
            throw invalid("EvaluationResult 写入身份不完整");
        }
        EvaluationCaseAttemptEntity attempt = attemptMapper.selectById(command.caseAttemptId());
        EvaluationCaseRunEntity caseRun = caseRunMapper.selectById(command.caseRunId());
        EvaluatorVersionEntity evaluatorVersion = evaluatorVersionMapper.selectById(command.evaluatorVersionId());
        if (attempt == null || caseRun == null || evaluatorVersion == null
                || !command.runId().equals(attempt.getRunId()) || !command.spaceId().equals(attempt.getSpaceId())
                || !command.caseRunId().equals(attempt.getCaseRunId())
                || !command.runId().equals(caseRun.getRunId()) || !command.spaceId().equals(caseRun.getSpaceId())
                || !command.testCaseVersionId().equals(caseRun.getTestCaseVersionId())
                || !command.spaceId().equals(evaluatorVersion.getSpaceId())
                || !EvaluationVersionStatus.PUBLISHED.name().equals(evaluatorVersion.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "EvaluationResult 写入身份或版本归属不一致");
        }
        if (!EvaluationAttemptStatus.EVALUATING.name().equals(attempt.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "CaseAttempt 当前不允许追加评价结果");
        }
        return new WriteIdentity(attempt, caseRun, evaluatorVersion);
    }

    /**
     * 校验写入命令参数合法性
     * 校验评估轮次、状态、摘要编码、时间区间；校验detailsJson格式；
     * 约束：ERROR/SKIPPED不能带指标，PASSED/FAILED必须携带指标
     */
    private void validateCommand(EvaluatorResultWriteCommand command) {
        if (command.evaluationAttemptNo() == null || command.evaluationAttemptNo() < 1
                || command.status() == null || command.summaryCode() == null
                || command.summaryCode().isBlank() || command.summaryCode().length() > MAX_SUMMARY_CODE_LENGTH
                || command.startedAt() == null || command.finishedAt() == null
                || command.finishedAt().isBefore(command.startedAt())) {
            throw invalid("EvaluationResult 内容无效");
        }
        validateJsonObject(command.detailsJson(), "detailsJson");
        List<StandardMetricOutput> metrics = safeList(command.metrics());
        if ((command.status() == EvaluationResultStatus.ERROR || command.status() == EvaluationResultStatus.SKIPPED)
                && !metrics.isEmpty()) {
            throw invalid("ERROR/SKIPPED Result 不允许产生质量 Metric");
        }
        if ((command.status() == EvaluationResultStatus.PASSED || command.status() == EvaluationResultStatus.FAILED)
                && metrics.isEmpty()) {
            throw invalid("PASSED/FAILED Result 必须产生标准 Metric");
        }
    }

    /**
     * 构建证据引用实体Map
     * 校验证据key唯一、字段长度、contentHash长度、locatorJson合法性；
     * 使用referenceKey作为本地引用key，用于后续指标关联
     */
    private Map<String, EvaluationEvidenceReferenceEntity> buildEvidence(EvaluatorResultWriteCommand command,
                                                                         Long resultId) {
        Map<String, EvaluationEvidenceReferenceEntity> values = new java.util.LinkedHashMap<>();
        for (EvidenceReferenceValue value : safeList(command.evidence())) {
            if (value == null || value.referenceKey() == null || value.referenceKey().isBlank()
                    || value.evidenceType() == null || value.businessId() == null || value.businessId().isBlank()
                    || value.businessId().length() > MAX_EVIDENCE_BUSINESS_ID_LENGTH
                    || value.contentHash() != null && value.contentHash().length() != 64
                    || value.summary() != null && value.summary().length() > MAX_EVIDENCE_SUMMARY_LENGTH
                    || values.containsKey(value.referenceKey())) {
                throw invalid("EvidenceReference 内容或 key 无效");
            }
            validateJsonObject(value.locatorJson(), "locatorJson");
            EvaluationEvidenceReferenceEntity entity = new EvaluationEvidenceReferenceEntity();
            entity.setId(IdWorker.getId());
            entity.setCaseAttemptId(command.caseAttemptId());
            entity.setResultId(resultId);
            entity.setSpaceId(command.spaceId());
            entity.setEvidenceType(value.evidenceType().name());
            entity.setBusinessId(value.businessId());
            entity.setContentHash(value.contentHash());
            entity.setSummary(value.summary());
            entity.setLocatorJson(value.locatorJson());
            values.put(value.referenceKey(), entity);
        }
        return values;
    }

    /**
     * 构建指标实体列表
     * 校验metricKey在本次写入内不重复；校验引用的证据key全部存在；
     * 通过MetricFactory生成指标实体
     */
    private List<EvaluationMetricEntity> buildMetrics(EvaluatorResultWriteCommand command, WriteIdentity identity,
                                                      Long resultId, Set<String> evidenceKeys) {
        Set<String> metricKeys = new HashSet<>();
        MetricWriteContext context = new MetricWriteContext(command.spaceId(), command.runId(), command.caseRunId(),
                command.caseAttemptId(), command.testCaseVersionId(), resultId, command.evaluatorVersionId(),
                resultId, identity.evaluatorVersion().getEvaluatorKey());
        return safeList(command.metrics()).stream().map(output -> {
            if (output == null || output.value() == null || !metricKeys.add(output.value().metricKey())
                    || !evidenceKeys.containsAll(safeList(output.evidenceReferenceKeys()))) {
                throw invalid("Metric key 重复或 Evidence 引用不存在");
            }
            return EvaluationMetricFactory.create(context, output.value());
        }).toList();
    }

    /**
     * 组装EvaluationResult主实体
     */
    private EvaluationResultEntity result(EvaluatorResultWriteCommand command,
                                          EvaluatorVersionEntity evaluatorVersion) {
        EvaluationResultEntity result = new EvaluationResultEntity();
        result.setId(IdWorker.getId());
        result.setSpaceId(command.spaceId());
        result.setRunId(command.runId());
        result.setCaseAttemptId(command.caseAttemptId());
        result.setEvaluatorVersionId(command.evaluatorVersionId());
        result.setEvaluationAttemptNo(command.evaluationAttemptNo());
        result.setStatus(command.status().name());
        result.setSummaryCode(command.summaryCode());
        result.setDetailsJson(command.detailsJson());
        result.setImplementationVersion(evaluatorVersion.getImplementationVersion());
        result.setTraceId(command.traceId());
        result.setSpanId(command.spanId());
        result.setStartedAt(command.startedAt());
        result.setFinishedAt(command.finishedAt());
        return result;
    }

    /**
     * 校验字符串为合法JSON对象
     */
    private void validateJsonObject(String json, String field) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonUtils.parse(json, new TypeReference<Map<String, Object>>() { });
        } catch (RuntimeException exception) {
            throw invalid(field + " 必须是 JSON object");
        }
    }

    /**
     * 空列表兜底，null转为空集合
     */
    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 快速构造BAD_REQUEST业务异常
     */
    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    /**
     * 写入身份载体，携带本次写入关联的Attempt、CaseRun、评估器版本信息
     */
    private record WriteIdentity(EvaluationCaseAttemptEntity attempt,
                                 EvaluationCaseRunEntity caseRun,
                                 EvaluatorVersionEntity evaluatorVersion) { }
}
