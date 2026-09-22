package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.MetricEvidenceReferenceVO;
import com.agentdoc.common.feign.vo.StandardMetricVO;
import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationFeedbackMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricEvidenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationEvidenceReferenceEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEvidenceEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import com.agentdoc.evaluation.pojo.vo.EvaluationResultDetailVO;
import com.agentdoc.evaluation.pojo.vo.EvaluationFeedbackVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_READ;

/**
 * 评估结果查询服务
 * 查询评估结果详情，关联指标与证据引用，组装详情VO并做引用完整性校验。
 */
@Service
@RequiredArgsConstructor
public class EvaluationResultQueryService {
    private final EvaluationResultMapper resultMapper;
    private final EvaluationMetricMapper metricMapper;
    private final EvaluationMetricEvidenceMapper metricEvidenceMapper;
    private final EvaluationEvidenceReferenceMapper evidenceMapper;
    private final EvaluationCaseAttemptMapper attemptMapper;
    private final EvaluationFeedbackMapper feedbackMapper;
    private final SpaceAccessService spaceAccessService;

    /**
     * 查询评估结果详情
     * 根据评估结果ID查询主记录，校验空间权限；
     * 关联指标、指标证据关联表、证据引用，组装VO并校验证据引用完整性
     */
    public EvaluationResultDetailVO detail(Long id) {
        EvaluationResultEntity result = resultMapper.selectById(id);
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "EvaluationResult 不存在");
        }
        spaceAccessService.requirePermission(result.getSpaceId(), EVALUATION_READ);
        List<EvaluationMetricEntity> metrics = metricMapper.selectList(
                new LambdaQueryWrapper<EvaluationMetricEntity>()
                        .eq(EvaluationMetricEntity::getEvaluationResultId, id)
                        .orderByAsc(EvaluationMetricEntity::getId));
        List<EvaluationMetricEvidenceEntity> links = metrics.isEmpty() ? List.of()
                : metricEvidenceMapper.selectList(new LambdaQueryWrapper<EvaluationMetricEvidenceEntity>()
                .in(EvaluationMetricEvidenceEntity::getMetricId,
                        metrics.stream().map(EvaluationMetricEntity::getId).toList()));
        Set<Long> linkedEvidenceIds = links.stream()
                .map(EvaluationMetricEvidenceEntity::getEvidenceReferenceId).collect(Collectors.toSet());
        List<EvaluationEvidenceReferenceEntity> evidence = evidenceMapper.selectList(
                new LambdaQueryWrapper<EvaluationEvidenceReferenceEntity>()
                        .eq(EvaluationEvidenceReferenceEntity::getResultId, id)
                        .orderByAsc(EvaluationEvidenceReferenceEntity::getId));
        Map<Long, EvaluationEvidenceReferenceEntity> evidenceById = evidence.stream()
                .collect(Collectors.toMap(EvaluationEvidenceReferenceEntity::getId, Function.identity()));
        Map<Long, List<MetricEvidenceReferenceVO>> evidenceByMetric = links.stream()
                .filter(link -> evidenceById.containsKey(link.getEvidenceReferenceId()))
                .collect(Collectors.groupingBy(EvaluationMetricEvidenceEntity::getMetricId,
                        Collectors.mapping(link -> evidenceVO(evidenceById.get(link.getEvidenceReferenceId())),
                                Collectors.toList())));
        List<StandardMetricVO> metricVOs = metrics.stream().map(metric -> metricVO(metric,
                evidenceByMetric.getOrDefault(metric.getId(), List.of()))).toList();
        List<MetricEvidenceReferenceVO> evidenceVOs = evidence.stream().map(EvaluationResultQueryService::evidenceVO)
                .toList();
        if (!evidenceById.keySet().containsAll(linkedEvidenceIds)) {
            throw new BusinessException(ErrorCode.CONFLICT, "EvaluationResult Evidence 引用不完整");
        }
        EvaluationCaseAttemptEntity attempt = attemptMapper.selectById(result.getCaseAttemptId());
        Long executionId = evidence.stream()
                .filter(value -> "AGENT_EXECUTION".equals(value.getEvidenceType()))
                .map(EvaluationEvidenceReferenceEntity::getBusinessId)
                .map(EvaluationResultQueryService::parseLong).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        List<EvaluationFeedbackVO> feedback = queryFeedback(result, attempt, executionId).stream()
                .map(EvaluationFeedbackVO::from).toList();
        return new EvaluationResultDetailVO(result.getId(), result.getSpaceId(), result.getRunId(),
                result.getCaseAttemptId(), result.getEvaluatorVersionId(), result.getEvaluationAttemptNo(),
                result.getStatus(), result.getScore(), result.getSummaryCode(), result.getDetailsJson(),
                result.getImplementationVersion(), result.getTraceId(), result.getSpanId(), result.getStartedAt(),
                result.getFinishedAt(), result.getCreatedAt(), metricVOs, evidenceVOs, feedback);
    }

    private List<EvaluationFeedbackEntity> queryFeedback(EvaluationResultEntity result,
                                                          EvaluationCaseAttemptEntity attempt,
                                                          Long executionId) {
        if (attempt == null) {
            return List.of();
        }
        LambdaQueryWrapper<EvaluationFeedbackEntity> query = new LambdaQueryWrapper<EvaluationFeedbackEntity>()
                .eq(EvaluationFeedbackEntity::getSpaceId, result.getSpaceId())
                .and(values -> {
                    values.eq(EvaluationFeedbackEntity::getCaseRunId, attempt.getCaseRunId());
                    if (attempt.getReplayTaskId() != null) {
                        values.or().eq(EvaluationFeedbackEntity::getTaskId, attempt.getReplayTaskId());
                    }
                    if (executionId != null) {
                        values.or().eq(EvaluationFeedbackEntity::getExecutionId, executionId);
                    }
                })
                .orderByAsc(EvaluationFeedbackEntity::getId);
        return feedbackMapper.selectList(query);
    }

    private static Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 指标实体转换为StandardMetricVO
     */
    private static StandardMetricVO metricVO(EvaluationMetricEntity metric,
                                             List<MetricEvidenceReferenceVO> evidence) {
        return new StandardMetricVO(metric.getId(), metric.getSpaceId(), metric.getRunId(),
                metric.getCaseRunId(), metric.getCaseAttemptId(), metric.getTestCaseVersionId(),
                metric.getEvaluationResultId(), metric.getEvaluatorVersionId(), metric.getContractVersion(),
                metric.getSource(), metric.getProducerId(), metric.getMetricKey(), metric.getValueType(),
                metric.getNumericValue(), metric.getBooleanValue(), metric.getStringValue(), metric.getUnit(),
                metric.getDirection(), metric.getCreatedAt(), evidence);
    }

    /**
     * 证据引用实体转换为证据VO
     */
    private static MetricEvidenceReferenceVO evidenceVO(EvaluationEvidenceReferenceEntity evidence) {
        return new MetricEvidenceReferenceVO(evidence.getId(), evidence.getEvidenceType(), evidence.getBusinessId(),
                evidence.getContentHash(), evidence.getSummary(), evidence.getLocatorJson());
    }
}
