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
 * 只读服务，提供评估结果单条详情查询能力；
 * 自动关联下属指标、指标绑定证据、结果级证据、人工反馈数据；
 * 同时做证据引用完整性校验，防止出现引用悬空数据。
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
     * <p>执行流程：
     * 1. 根据评估结果ID查询主记录，不存在则抛出404
     * 2. 校验用户在该空间拥有评估读权限
     * 3. 查询归属该评估结果下的全部指标
     * 4. 批量查询【指标-证据】关联中间表，拿到指标绑定的证据ID集合
     * 5. 查询属于当前评估结果的所有证据引用主记录
     * 6. 校验：所有被指标关联的证据，必须存在于本结果的证据集合中，检测引用悬空
     * 7. 指标实体+绑定证据组装为StandardMetricVO
     * 8. 结果级证据组装为证据VO列表
     * 9. 查询CaseAttempt，提取AGENT_EXECUTION类型证据中的executionId
     * 10. 多条件关联查询人工反馈记录并转VO
     * 11. 组装并返回完整详情VO
     * </p>
     * @param id 评估结果主键ID
     * @return 包含主信息、指标、证据、反馈的完整详情VO
     */
    public EvaluationResultDetailVO detail(Long id) {
        // 查询评估结果主记录
        EvaluationResultEntity result = resultMapper.selectById(id);
        if (result == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "EvaluationResult 不存在");
        }
        // 空间维度权限校验
        spaceAccessService.requirePermission(result.getSpaceId(), EVALUATION_READ);

        // 查询当前评估结果下所有指标，按主键升序
        List<EvaluationMetricEntity> metrics = metricMapper.selectList(
                new LambdaQueryWrapper<EvaluationMetricEntity>()
                        .eq(EvaluationMetricEntity::getEvaluationResultId, id)
                        .orderByAsc(EvaluationMetricEntity::getId));

        List<EvaluationMetricEvidenceEntity> links = metrics.isEmpty() ? List.of()
                : metricEvidenceMapper.selectList(new LambdaQueryWrapper<EvaluationMetricEvidenceEntity>()
                // IN批量查询指标证据关联，避免N+1
                .in(EvaluationMetricEvidenceEntity::getMetricId,
                        metrics.stream().map(EvaluationMetricEntity::getId).toList()));

        // 收集所有被指标引用的证据ID
        Set<Long> linkedEvidenceIds = links.stream()
                .map(EvaluationMetricEvidenceEntity::getEvidenceReferenceId)
                .collect(Collectors.toSet());

        // 查询归属本评估结果的全部证据引用（结果级证据）
        List<EvaluationEvidenceReferenceEntity> evidence = evidenceMapper.selectList(
                new LambdaQueryWrapper<EvaluationEvidenceReferenceEntity>()
                        .eq(EvaluationEvidenceReferenceEntity::getResultId, id)
                        .orderByAsc(EvaluationEvidenceReferenceEntity::getId));

        // 证据ID -> 证据实体，用于快速查找
        Map<Long, EvaluationEvidenceReferenceEntity> evidenceById = evidence.stream()
                .collect(Collectors.toMap(EvaluationEvidenceReferenceEntity::getId, Function.identity()));

        // 按指标ID分组，每个指标绑定它对应的证据VO列表
        Map<Long, List<MetricEvidenceReferenceVO>> evidenceByMetric = links.stream()
                // 过滤掉在本结果证据表中不存在的引用（提前过滤脏关联）
                .filter(link -> evidenceById.containsKey(link.getEvidenceReferenceId()))
                .collect(Collectors.groupingBy(EvaluationMetricEvidenceEntity::getMetricId,
                        Collectors.mapping(link -> evidenceVO(evidenceById.get(link.getEvidenceReferenceId())),
                                Collectors.toList())));

        // 指标实体转VO，注入绑定的证据
        List<StandardMetricVO> metricVOs = metrics.stream().map(metric -> metricVO(metric,
                evidenceByMetric.getOrDefault(metric.getId(), List.of()))).toList();

        // 结果级证据统一转VO
        List<MetricEvidenceReferenceVO> evidenceVOs = evidence.stream()
                .map(EvaluationResultQueryService::evidenceVO)
                .toList();

        // 【引用完整性强校验】
        // 所有被指标关联的证据ID，必须全部存在于当前result的证据集合中；
        // 若存在引用了不属于本result的证据，判定数据不一致，抛异常。
        if (!evidenceById.keySet().containsAll(linkedEvidenceIds)) {
            throw new BusinessException(ErrorCode.CONFLICT, "EvaluationResult Evidence 引用不完整");
        }

        // 查询本次评估所属的用例尝试记录
        EvaluationCaseAttemptEntity attempt = attemptMapper.selectById(result.getCaseAttemptId());

        // 从证据中提取AGENT_EXECUTION类型的业务ID，作为executionId，用于反馈关联
        Long executionId = evidence.stream()
                .filter(value -> "AGENT_EXECUTION".equals(value.getEvidenceType()))
                .map(EvaluationEvidenceReferenceEntity::getBusinessId)
                .map(EvaluationResultQueryService::parseLong)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);

        // 查询关联人工反馈记录，并转换为VO
        List<EvaluationFeedbackVO> feedback = queryFeedback(result, attempt, executionId).stream()
                .map(EvaluationFeedbackVO::from).toList();

        // 组装完整详情返回
        return new EvaluationResultDetailVO(result.getId(), result.getSpaceId(), result.getRunId(),
                result.getCaseAttemptId(), result.getEvaluatorVersionId(), result.getEvaluationAttemptNo(),
                result.getStatus(), result.getScore(), result.getSummaryCode(), result.getDetailsJson(),
                result.getImplementationVersion(), result.getTraceId(), result.getSpanId(), result.getStartedAt(),
                result.getFinishedAt(), result.getCreatedAt(), metricVOs, evidenceVOs, feedback);
    }

    /**
     * 查询关联人工反馈记录
     * <p>多条件或逻辑：
     * 1. 必选：spaceId + caseRunId
     * 2. 可选或条件：executionTaskId / executionId，满足其一即可命中
     * 按主键升序返回反馈列表
     * </p>
     * @param result 评估结果主实体
     * @param attempt 用例尝试记录
     * @param executionId Agent执行ID（从证据解析得来）
     * @return 原始反馈实体列表
     */
    private List<EvaluationFeedbackEntity> queryFeedback(EvaluationResultEntity result,
                                                         EvaluationCaseAttemptEntity attempt,
                                                         Long executionId) {
        // 没有attempt则缺少caseRunId，无法关联反馈，直接返回空
        if (attempt == null) {
            return List.of();
        }
        LambdaQueryWrapper<EvaluationFeedbackEntity> query = new LambdaQueryWrapper<EvaluationFeedbackEntity>()
                .eq(EvaluationFeedbackEntity::getSpaceId, result.getSpaceId())
                .and(values -> {
                    // 基础条件：同一caseRun
                    values.eq(EvaluationFeedbackEntity::getCaseRunId, attempt.getCaseRunId());
                    // 可选：回放任务ID
                    if (attempt.getExecutionTaskId() != null) {
                        values.or().eq(EvaluationFeedbackEntity::getTaskId, attempt.getExecutionTaskId());
                    }
                    // 可选：Agent执行ID
                    if (executionId != null) {
                        values.or().eq(EvaluationFeedbackEntity::getExecutionId, executionId);
                    }
                })
                .orderByAsc(EvaluationFeedbackEntity::getId);
        return feedbackMapper.selectList(query);
    }

    /**
     * 安全字符串转Long工具方法
     * 转换失败 / 入参null时返回null，不抛出数字格式异常
     * @param value 待转换字符串
     * @return 转换后的Long，失败返回null
     */
    private static Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 指标实体转换为StandardMetricVO
     * @param metric 指标数据库实体
     * @param evidence 该指标绑定的证据VO列表
     * @return 指标视图对象
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
     * @param evidence 证据引用数据库实体
     * @return 证据视图对象
     */
    private static MetricEvidenceReferenceVO evidenceVO(EvaluationEvidenceReferenceEntity evidence) {
        return new MetricEvidenceReferenceVO(evidence.getId(), evidence.getEvidenceType(), evidence.getBusinessId(),
                evidence.getContentHash(), evidence.getSummary(), evidence.getLocatorJson());
    }
}
