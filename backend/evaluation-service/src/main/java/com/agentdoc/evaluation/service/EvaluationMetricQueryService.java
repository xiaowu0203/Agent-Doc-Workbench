package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.MetricComparisonQueryDTO;
import com.agentdoc.common.feign.vo.MetricComparisonInputVO;
import com.agentdoc.common.feign.vo.MetricComparisonRowVO;
import com.agentdoc.common.feign.vo.MetricComparisonValueVO;
import com.agentdoc.common.feign.vo.MetricEvidenceReferenceVO;
import com.agentdoc.common.feign.vo.StandardMetricVO;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.evaluation.enums.EvaluationMetricSelection;
import com.agentdoc.evaluation.enums.EvaluationMetricSource;
import com.agentdoc.evaluation.enums.EvaluationAttemptStatus;
import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricEvidenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.metric.MetricEffectiveProjection;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationEvidenceReferenceEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEvidenceEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import com.agentdoc.evaluation.pojo.param.MetricSearchParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_READ;
import static com.agentdoc.evaluation.constant.EvaluationConstant.MAX_COMPARISON_RUN_COUNT;
import static com.agentdoc.evaluation.constant.EvaluationConstant.MAX_METRIC_QUERY_IDS;
import static com.agentdoc.evaluation.constant.EvaluationConstant.MAX_METRIC_QUERY_KEYS;
import static com.agentdoc.evaluation.constant.EvaluationConstant.MAX_METRIC_QUERY_ROWS;

/**
 * 指标查询服务
 * 提供指标分页检索、多Run指标对比能力；
 * 内置有效指标投影过滤，关联证据引用，校验指标契约一致性，组装对比行数据。
 */
@Service
@RequiredArgsConstructor
public class EvaluationMetricQueryService {
    /**
     * 指标缺失默认提示文案
     */
    private static final String MISSING_EFFECTIVE_METRIC = "FACT_UNAVAILABLE";

    private final EvaluationMetricMapper metricMapper;
    private final EvaluationCaseRunMapper caseRunMapper;
    private final EvaluationCaseAttemptMapper attemptMapper;
    private final EvaluationResultMapper resultMapper;
    private final EvaluationMetricEvidenceMapper metricEvidenceMapper;
    private final EvaluationEvidenceReferenceMapper evidenceMapper;
    private final EvaluationRunMapper runMapper;
    private final SpaceAccessService spaceAccessService;

    /**
     * 分页搜索指标
     * 校验入参与空间权限，查询指标原始数据；
     * 根据selection决定是否执行有效指标投影，排序后分页，转换为VO返回分页对象
     */
    public PageVO<StandardMetricVO> search(MetricSearchParam param) {
        param.validate();
        spaceAccessService.requirePermission(param.getSpaceId(), EVALUATION_READ);
        List<EvaluationMetricEntity> metrics = query(param.getSpaceId(), param.getRunIds(),
                param.getTestCaseVersionIds(), param.getMetricKeys(), sourceNames(param));
        if (param.getSelection() == EvaluationMetricSelection.EFFECTIVE) {
            metrics = effective(metrics);
        }
        metrics = sort(metrics);
        int from = Math.min((param.getPageNum() - 1) * param.getPageSize(), metrics.size());
        int to = Math.min(from + param.getPageSize(), metrics.size());
        List<StandardMetricVO> records = toVO(metrics.subList(from, to));
        return PageVO.of(records, metrics.size(), param);
    }

    /**
     * 获取多Run指标对比输入数据
     * 校验对比请求，校验run归属，加载有效指标；
     * 按【用例版本+metricKey】分组对齐，填充缺失原因，组装对比行VO
     */
    public MetricComparisonInputVO comparisonInput(MetricComparisonQueryDTO request) {
        List<Long> runIds = validateComparisonRequest(request);
        spaceAccessService.requirePermission(request.spaceId(), EVALUATION_READ);
        requireRuns(request.spaceId(), runIds);
        List<EvaluationMetricEntity> metrics = sort(effective(query(request.spaceId(), runIds,
                request.testCaseVersionIds(), request.metricKeys(), List.of())));
        List<StandardMetricVO> values = toVO(metrics);

        Map<AlignmentKey, List<StandardMetricVO>> groups = values.stream().collect(Collectors.groupingBy(
                value -> new AlignmentKey(value.testCaseVersionId(), value.metricKey()),
                LinkedHashMap::new, Collectors.toList()));
        Map<MissingKey, String> missingReasons = loadMissingReasons(runIds, groups);
        List<MetricComparisonRowVO> rows = new ArrayList<>();
        for (Map.Entry<AlignmentKey, List<StandardMetricVO>> entry : groups.entrySet()) {
            StandardMetricVO contract = entry.getValue().getFirst();
            requireSameContract(entry.getKey(), contract, entry.getValue());
            Map<Long, StandardMetricVO> metricByRun = new HashMap<>();
            for (StandardMetricVO metric : entry.getValue()) {
                if (metricByRun.put(metric.runId(), metric) != null) {
                    throw new BusinessException(ErrorCode.CONFLICT,
                            "同一 Run 的对齐键存在多个有效 Metric: " + entry.getKey());
                }
            }
            List<MetricComparisonValueVO> alignedValues = runIds.stream()
                    .map(runId -> metricByRun.containsKey(runId)
                            ? new MetricComparisonValueVO(runId, metricByRun.get(runId), null)
                            : new MetricComparisonValueVO(runId, null, missingReasons.getOrDefault(
                            new MissingKey(runId, entry.getKey().testCaseVersionId(),
                                    contract.evaluatorVersionId()), MISSING_EFFECTIVE_METRIC)))
                    .toList();
            rows.add(new MetricComparisonRowVO(entry.getKey().testCaseVersionId(), entry.getKey().metricKey(),
                    contract.contractVersion(), contract.valueType(), contract.unit(), contract.direction(),
                    contract.source(), alignedValues));
        }
        return new MetricComparisonInputVO(rows);
    }

    /**
     * 加载指标缺失原因
     * 查询caseRun、attempt、评估结果，识别任务失败、取消、评估器报错/跳过等场景；
     * 生成MissingKey -> 缺失原因映射，用于指标对比页面展示
     */
    private Map<MissingKey, String> loadMissingReasons(
            List<Long> runIds,
            Map<AlignmentKey, List<StandardMetricVO>> groups) {
        Set<Long> evaluatorVersionIds = groups.values().stream().flatMap(List::stream)
                .filter(metric -> EvaluationMetricSource.EVALUATOR.name().equals(metric.source()))
                .map(StandardMetricVO::evaluatorVersionId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (evaluatorVersionIds.isEmpty()) {
            return Map.of();
        }
        List<EvaluationCaseRunEntity> caseRuns = caseRunMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseRunEntity>()
                        .in(EvaluationCaseRunEntity::getRunId, runIds));
        Map<Long, EvaluationCaseRunEntity> caseRunByAttempt = caseRuns.stream()
                .filter(caseRun -> caseRun.getCurrentAttemptId() != null)
                .collect(Collectors.toMap(EvaluationCaseRunEntity::getCurrentAttemptId, Function.identity()));
        if (caseRunByAttempt.isEmpty()) {
            return Map.of();
        }
        Map<Long, EvaluationCaseAttemptEntity> attempts = attemptMapper.selectBatchIds(caseRunByAttempt.keySet())
                .stream().collect(Collectors.toMap(EvaluationCaseAttemptEntity::getId, Function.identity()));
        Map<MissingKey, String> reasons = new HashMap<>();
        for (Map.Entry<Long, EvaluationCaseRunEntity> entry : caseRunByAttempt.entrySet()) {
            EvaluationCaseAttemptEntity attempt = attempts.get(entry.getKey());
            if (attempt == null) {
                continue;
            }
            String reason = switch (EvaluationAttemptStatus.valueOf(attempt.getStatus())) {
                case REPLAY_FAILED -> "REPLAY_FAILED";
                case CANCEL_PENDING, CANCELED -> "CANCELED";
                default -> null;
            };
            if (reason != null) {
                for (Long evaluatorVersionId : evaluatorVersionIds) {
                    EvaluationCaseRunEntity caseRun = entry.getValue();
                    reasons.put(new MissingKey(caseRun.getRunId(), caseRun.getTestCaseVersionId(),
                            evaluatorVersionId), reason);
                }
            }
        }
        List<EvaluationResultEntity> results = resultMapper.selectList(
                new LambdaQueryWrapper<EvaluationResultEntity>()
                        .in(EvaluationResultEntity::getCaseAttemptId, caseRunByAttempt.keySet())
                        .in(EvaluationResultEntity::getEvaluatorVersionId, evaluatorVersionIds));
        Map<ResultKey, EvaluationResultEntity> latest = new HashMap<>();
        for (EvaluationResultEntity result : results) {
            ResultKey key = new ResultKey(result.getCaseAttemptId(), result.getEvaluatorVersionId());
            latest.merge(key, result, EvaluationMetricQueryService::latestResult);
        }
        for (Map.Entry<ResultKey, EvaluationResultEntity> entry : latest.entrySet()) {
            String status = entry.getValue().getStatus();
            if (!EvaluationResultStatus.ERROR.name().equals(status)
                    && !EvaluationResultStatus.SKIPPED.name().equals(status)) {
                continue;
            }
            EvaluationCaseRunEntity caseRun = caseRunByAttempt.get(entry.getKey().caseAttemptId());
            reasons.put(new MissingKey(caseRun.getRunId(), caseRun.getTestCaseVersionId(),
                    entry.getKey().evaluatorVersionId()), EvaluationResultStatus.ERROR.name().equals(status)
                    ? "EVALUATOR_ERROR" : "EVALUATOR_SKIPPED");
        }
        return reasons;
    }

    /**
     * 基础指标查询
     * 组装查询条件，按空间、run、用例版本、metricKey、来源过滤；
     * 增加最大行数限制，结果超限抛出业务异常
     */
    private List<EvaluationMetricEntity> query(Long spaceId, List<Long> runIds,
                                               List<Long> testCaseVersionIds, List<String> metricKeys,
                                               List<String> sources) {
        LambdaQueryWrapper<EvaluationMetricEntity> query = new LambdaQueryWrapper<EvaluationMetricEntity>()
                .eq(EvaluationMetricEntity::getSpaceId, spaceId)
                .in(notEmpty(runIds), EvaluationMetricEntity::getRunId, runIds)
                .in(notEmpty(testCaseVersionIds), EvaluationMetricEntity::getTestCaseVersionId, testCaseVersionIds)
                .in(notEmpty(metricKeys), EvaluationMetricEntity::getMetricKey, metricKeys)
                .in(notEmpty(sources), EvaluationMetricEntity::getSource, sources)
                .last("LIMIT " + MAX_METRIC_QUERY_ROWS);
        List<EvaluationMetricEntity> metrics = metricMapper.selectList(query);
        if (metrics.size() >= MAX_METRIC_QUERY_ROWS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Metric 查询结果过多，请缩小过滤范围");
        }
        return metrics;
    }

    /**
     * 过滤有效指标
     * 调用MetricEffectiveProjection投影；额外过滤已取消的attempt，执行型指标不受取消状态影响
     */
    private List<EvaluationMetricEntity> effective(List<EvaluationMetricEntity> metrics) {
        if (metrics.isEmpty()) {
            return metrics;
        }
        Set<Long> caseRunIds = metrics.stream().map(EvaluationMetricEntity::getCaseRunId)
                .collect(Collectors.toSet());
        List<EvaluationCaseRunEntity> caseRuns = caseRunMapper.selectBatchIds(caseRunIds);
        Set<Long> currentAttemptIds = caseRuns.stream().map(EvaluationCaseRunEntity::getCurrentAttemptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> canceledAttemptIds = currentAttemptIds.isEmpty() ? Set.of()
                : attemptMapper.selectBatchIds(currentAttemptIds).stream()
                .filter(attempt -> EvaluationAttemptStatus.CANCELED.name().equals(attempt.getStatus())
                        || EvaluationAttemptStatus.CANCEL_PENDING.name().equals(attempt.getStatus()))
                .map(EvaluationCaseAttemptEntity::getId).collect(Collectors.toSet());
        Set<Long> evaluatorVersionIds = metrics.stream().map(EvaluationMetricEntity::getEvaluatorVersionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<EvaluationResultEntity> results = currentAttemptIds.isEmpty() || evaluatorVersionIds.isEmpty()
                ? List.of()
                : resultMapper.selectList(new LambdaQueryWrapper<EvaluationResultEntity>()
                .in(EvaluationResultEntity::getCaseAttemptId, currentAttemptIds)
                .in(EvaluationResultEntity::getEvaluatorVersionId, evaluatorVersionIds));
        List<EvaluationMetricEntity> selected = MetricEffectiveProjection.select(metrics, caseRuns, results);
        return selected.stream().filter(metric -> !canceledAttemptIds.contains(metric.getCaseAttemptId())
                || EvaluationMetricSource.EXECUTION.name().equals(metric.getSource())).toList();
    }

    /**
     * 指标实体转VO
     * 批量关联指标-证据关联表、证据引用表，组装指标附带的证据列表
     */
    private List<StandardMetricVO> toVO(List<EvaluationMetricEntity> metrics) {
        if (metrics.isEmpty()) {
            return List.of();
        }
        List<Long> metricIds = metrics.stream().map(EvaluationMetricEntity::getId).toList();
        List<EvaluationMetricEvidenceEntity> links = metricEvidenceMapper.selectList(
                new LambdaQueryWrapper<EvaluationMetricEvidenceEntity>()
                        .in(EvaluationMetricEvidenceEntity::getMetricId, metricIds));
        Set<Long> evidenceIds = links.stream().map(EvaluationMetricEvidenceEntity::getEvidenceReferenceId)
                .collect(Collectors.toSet());
        Map<Long, EvaluationEvidenceReferenceEntity> evidenceById = evidenceIds.isEmpty()
                ? Map.of()
                : evidenceMapper.selectBatchIds(evidenceIds).stream().collect(Collectors.toMap(
                EvaluationEvidenceReferenceEntity::getId, Function.identity()));
        Map<Long, List<MetricEvidenceReferenceVO>> evidenceByMetric = links.stream()
                .filter(link -> evidenceById.containsKey(link.getEvidenceReferenceId()))
                .collect(Collectors.groupingBy(EvaluationMetricEvidenceEntity::getMetricId,
                        Collectors.mapping(link -> evidenceVO(evidenceById.get(link.getEvidenceReferenceId())),
                                Collectors.toList())));
        return metrics.stream().map(metric -> metricVO(metric,
                evidenceByMetric.getOrDefault(metric.getId(), List.of()))).toList();
    }

    /**
     * 校验对比使用的Run集合
     * 校验run存在、归属同一空间
     */
    private void requireRuns(Long spaceId, List<Long> runIds) {
        List<EvaluationRunEntity> runs = runMapper.selectBatchIds(runIds);
        if (runs.size() != runIds.size() || runs.stream().anyMatch(run -> !spaceId.equals(run.getSpaceId()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "对比 Run 必须存在且属于同一空间");
        }
    }

    /**
     * 校验指标对比查询入参
     * 校验spaceId、runId数量与合法性，校验过滤列表长度上限，返回去重后的runId列表
     */
    private static List<Long> validateComparisonRequest(MetricComparisonQueryDTO request) {
        if (request == null || request.spaceId() == null || request.spaceId() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "spaceId 必须为正整数");
        }
        List<Long> runIds = request.runIds() == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(request.runIds()));
        if (runIds.size() < 2 || runIds.size() > MAX_COMPARISON_RUN_COUNT
                || runIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "runIds 必须包含 2~" + MAX_COMPARISON_RUN_COUNT + " 个有效 ID");
        }
        requireFilterSize(request.testCaseVersionIds(), MAX_METRIC_QUERY_IDS, "testCaseVersionIds");
        requireFilterSize(request.metricKeys(), MAX_METRIC_QUERY_KEYS, "metricKeys");
        return runIds;
    }

    /**
     * 校验同一对齐键下所有指标契约一致
     * 指标类型、单位、方向、来源、契约版本必须完全相同，否则拒绝对比
     */
    private static void requireSameContract(AlignmentKey key, StandardMetricVO expected,
                                            List<StandardMetricVO> metrics) {
        boolean conflict = metrics.stream().anyMatch(metric ->
                !Objects.equals(expected.contractVersion(), metric.contractVersion())
                        || !Objects.equals(expected.valueType(), metric.valueType())
                        || !Objects.equals(expected.unit(), metric.unit())
                        || !Objects.equals(expected.direction(), metric.direction())
                        || !Objects.equals(expected.source(), metric.source()));
        if (conflict) {
            throw new BusinessException(ErrorCode.CONFLICT, "Metric 契约不一致，拒绝生成对比输入: " + key);
        }
    }

    /**
     * 指标排序：runId -> 用例版本 -> metricKey -> 主键ID
     */
    private static List<EvaluationMetricEntity> sort(List<EvaluationMetricEntity> metrics) {
        return metrics.stream().sorted(Comparator.comparing(EvaluationMetricEntity::getRunId)
                .thenComparing(EvaluationMetricEntity::getTestCaseVersionId)
                .thenComparing(EvaluationMetricEntity::getMetricKey)
                .thenComparing(EvaluationMetricEntity::getId)).toList();
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

    /**
     * 将枚举来源转为名称字符串
     */
    private static List<String> sourceNames(MetricSearchParam param) {
        return param.getSources() == null ? List.of()
                : param.getSources().stream().map(Enum::name).toList();
    }

    /**
     * 判断集合非空
     */
    private static boolean notEmpty(List<?> values) {
        return values != null && !values.isEmpty();
    }

    /**
     * 校验过滤参数列表长度上限
     */
    private static void requireFilterSize(List<?> values, int maxSize, String field) {
        if (values != null && values.size() > maxSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 最多允许 " + maxSize + " 项");
        }
    }

    /**
     * 取两个评估结果中最新记录
     * 优先按评估重试号evaluationAttemptNo，相同则按主键ID
     */
    private static EvaluationResultEntity latestResult(EvaluationResultEntity left,
                                                       EvaluationResultEntity right) {
        int attemptCompare = left.getEvaluationAttemptNo().compareTo(right.getEvaluationAttemptNo());
        if (attemptCompare != 0) {
            return attemptCompare > 0 ? left : right;
        }
        return left.getId() >= right.getId() ? left : right;
    }

    /**
     * 指标对齐分组Key：用例版本ID + metricKey
     */
    private record AlignmentKey(Long testCaseVersionId, String metricKey) { }

    /**
     * 评估结果分组Key：用例尝试ID + 评估器版本ID
     */
    private record ResultKey(Long caseAttemptId, Long evaluatorVersionId) { }

    /**
     * 指标缺失原因Key：runId + 用例版本ID + 评估器版本ID
     */
    private record MissingKey(Long runId, Long testCaseVersionId, Long evaluatorVersionId) { }
}
