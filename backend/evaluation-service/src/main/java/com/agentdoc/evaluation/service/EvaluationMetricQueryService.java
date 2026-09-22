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
 * 能力：指标分页检索、多Run指标横向对比；
 * 核心逻辑：有效指标投影过滤、关联证据引用加载、指标契约一致性校验、对比行数据对齐组装、缺失指标原因识别。
 * 说明：该服务只做只读查询与视图组装，不写入任何业务数据；权限校验统一依赖 SpaceAccessService 空间鉴权。
 */
@Service
@RequiredArgsConstructor
public class EvaluationMetricQueryService {
    /**
     * 指标缺失默认提示文案
     * 当找不到有效指标且没有识别到具体失败原因时，前端展示该占位文本
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
     * <p>执行流程：
     * 1. 参数自校验
     * 2. 校验当前用户在目标空间拥有指标读权限
     * 3. 根据条件查询原始指标实体列表
     * 4. 如果选择【仅有效指标】模式，执行有效指标投影过滤
     * 5. 按预定义规则排序
     * 6. 内存分页切片（注意：是查出全部符合条件数据后内存分页，不是数据库分页）
     * 7. 实体转VO并批量关联证据，返回分页视图对象
     * </p>
     * @param param 指标搜索入参，包含空间ID、runId列表、过滤条件、分页参数、指标选择模式
     * @return 分页VO，携带当前页记录 + 总条数
     */
    public PageVO<StandardMetricVO> search(MetricSearchParam param) {
        // 入参字段合法性校验
        param.validate();
        // 校验空间读权限
        spaceAccessService.requirePermission(param.getSpaceId(), EVALUATION_READ);
        // 查询原始指标数据
        List<EvaluationMetricEntity> metrics = query(param.getSpaceId(), param.getRunIds(),
                param.getTestCaseVersionIds(), param.getMetricKeys(), sourceNames(param));
        // 如果是仅查询有效指标，执行投影过滤逻辑
        if (param.getSelection() == EvaluationMetricSelection.EFFECTIVE) {
            metrics = effective(metrics);
        }
        // 执行指标排序
        metrics = sort(metrics);
        // 内存分页切片，计算起止下标
        int from = Math.min((param.getPageNum() - 1) * param.getPageSize(), metrics.size());
        int to = Math.min(from + param.getPageSize(), metrics.size());
        // 切片子集转VO（附带证据关联）
        List<StandardMetricVO> records = toVO(metrics.subList(from, to));
        return PageVO.of(records, metrics.size(), param);
    }

    /**
     * 获取多Run指标对比输入数据
     * <p>用于指标对比页面，把多条run的指标按【用例版本+metricKey】横向对齐成表格行
     * 执行流程：
     * 1. 校验对比请求参数合法性、run数量限制
     * 2. 校验空间读权限
     * 3. 校验所有待对比run归属同一个空间、且都存在
     * 4. 查询原始指标 + 过滤有效指标 + 排序
     * 5. 实体转VO
     * 6. 按 AlignmentKey（用例版本+metricKey）分组
     * 7. 加载缺失指标原因（任务取消、回放失败、评估器报错/跳过）
     * 8. 契约一致性校验：同一对齐key下所有run的指标元信息必须完全一致，否则抛异常
     * 9. 按传入run顺序横向填充值，缺失项绑定缺失原因，组装对比行
     * </p>
     * @param request 多Run对比查询DTO，包含空间ID、待对比runId集合、过滤条件
     * @return 组装完成的对比输入VO，每一行代表同一个指标在多个run下的取值集合
     */
    public MetricComparisonInputVO comparisonInput(MetricComparisonQueryDTO request) {
        // 参数校验，返回去重后的runId列表
        List<Long> runIds = validateComparisonRequest(request);
        // 空间读权限校验
        spaceAccessService.requirePermission(request.spaceId(), EVALUATION_READ);
        // 校验run都存在且归属同一空间
        requireRuns(request.spaceId(), runIds);
        // 查询原始指标 -> 过滤有效指标 -> 排序
        List<EvaluationMetricEntity> metrics = sort(effective(query(request.spaceId(), runIds,
                request.testCaseVersionIds(), request.metricKeys(), List.of())));
        List<StandardMetricVO> values = toVO(metrics);

        // 按【用例版本ID + metricKey】分组，使用LinkedHashMap保证分组顺序稳定
        Map<AlignmentKey, List<StandardMetricVO>> groups = values.stream().collect(Collectors.groupingBy(
                value -> new AlignmentKey(value.testCaseVersionId(), value.metricKey()),
                LinkedHashMap::new, Collectors.toList()));
        // 预加载缺失指标对应的业务原因
        Map<MissingKey, String> missingReasons = loadMissingReasons(runIds, groups);
        List<MetricComparisonRowVO> rows = new ArrayList<>();

        // 遍历每一组对齐key，生成一行对比数据
        for (Map.Entry<AlignmentKey, List<StandardMetricVO>> entry : groups.entrySet()) {
            // 取本组第一条作为契约参考模板
            StandardMetricVO contract = entry.getValue().getFirst();
            // 校验本组所有指标契约定义完全一致，不一致无法横向对比
            requireSameContract(entry.getKey(), contract, entry.getValue());

            // runId -> 该run下的指标VO，用于快速查找
            Map<Long, StandardMetricVO> metricByRun = new HashMap<>();
            for (StandardMetricVO metric : entry.getValue()) {
                // 同一个run内同一个对齐key不能出现多条有效指标，冲突直接抛异常
                if (metricByRun.put(metric.runId(), metric) != null) {
                    throw new BusinessException(ErrorCode.CONFLICT,
                            "同一 Run 的对齐键存在多个有效 Metric: " + entry.getKey());
                }
            }

            // 严格按照入参runIds顺序生成横向单元格；存在指标则填充值，不存在填充缺失原因
            List<MetricComparisonValueVO> alignedValues = runIds.stream()
                    .map(runId -> metricByRun.containsKey(runId)
                            ? new MetricComparisonValueVO(runId, metricByRun.get(runId), null)
                            : new MetricComparisonValueVO(runId, null, missingReasons.getOrDefault(
                            new MissingKey(runId, entry.getKey().testCaseVersionId(),
                                    contract.evaluatorVersionId()), MISSING_EFFECTIVE_METRIC)))
                    .toList();
            // 组装对比行，携带指标契约元信息 + 各run取值数组
            rows.add(new MetricComparisonRowVO(entry.getKey().testCaseVersionId(), entry.getKey().metricKey(),
                    contract.contractVersion(), contract.valueType(), contract.unit(), contract.direction(),
                    contract.source(), alignedValues));
        }
        return new MetricComparisonInputVO(rows);
    }

    /**
     * 加载指标缺失原因映射
     * <p>当某个(run,用例版本,评估器版本)组合没有产出有效指标时，识别为什么缺失：
     * 1. 任务回放失败 REPLAY_FAILED
     * 2. 任务已取消 CANCELED
     * 3. 评估器执行报错 EVALUATOR_ERROR
     * 4. 评估器被跳过 EVALUATOR_SKIPPED
     * 未命中以上场景时，使用默认占位文案 MISSING_EFFECTIVE_METRIC
     * </p>
     * @param runIds 当前对比的run列表
     * @param groups 已经按对齐key分好组的指标数据，用于提取需要覆盖的evaluatorVersionId集合
     * @return Map<MissingKey, 缺失原因编码>，key为(runId,用例版本,评估器版本)三元组
     */
    private Map<MissingKey, String> loadMissingReasons(
            List<Long> runIds,
            Map<AlignmentKey, List<StandardMetricVO>> groups) {
        // 收集所有评估器来源指标对应的评估器版本ID，非评估器指标不需要加载失败原因
        Set<Long> evaluatorVersionIds = groups.values().stream().flatMap(List::stream)
                .filter(metric -> EvaluationMetricSource.EVALUATOR.name().equals(metric.source()))
                .map(StandardMetricVO::evaluatorVersionId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (evaluatorVersionIds.isEmpty()) {
            return Map.of();
        }
        // 查询caseRun，拿到caseRun与currentAttemptId的关联
        List<EvaluationCaseRunEntity> caseRuns = caseRunMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseRunEntity>()
                        .in(EvaluationCaseRunEntity::getRunId, runIds));
        Map<Long, EvaluationCaseRunEntity> caseRunByAttempt = caseRuns.stream()
                .filter(caseRun -> caseRun.getCurrentAttemptId() != null)
                .collect(Collectors.toMap(EvaluationCaseRunEntity::getCurrentAttemptId, Function.identity()));
        if (caseRunByAttempt.isEmpty()) {
            return Map.of();
        }
        // 批量查询attempt记录，获取任务状态
        Map<Long, EvaluationCaseAttemptEntity> attempts = attemptMapper.selectBatchIds(caseRunByAttempt.keySet())
                .stream().collect(Collectors.toMap(EvaluationCaseAttemptEntity::getId, Function.identity()));

        Map<MissingKey, String> reasons = new HashMap<>();
        // 遍历attempt识别回放失败、取消状态
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
            // 匹配到失败/取消，则该caseRun下所有评估器版本都标记该缺失原因
            if (reason != null) {
                for (Long evaluatorVersionId : evaluatorVersionIds) {
                    EvaluationCaseRunEntity caseRun = entry.getValue();
                    reasons.put(new MissingKey(caseRun.getRunId(), caseRun.getTestCaseVersionId(),
                            evaluatorVersionId), reason);
                }
            }
        }

        // 查询评估结果表，识别评估器ERROR / SKIPPED
        List<EvaluationResultEntity> results = resultMapper.selectList(
                new LambdaQueryWrapper<EvaluationResultEntity>()
                        .in(EvaluationResultEntity::getCaseAttemptId, caseRunByAttempt.keySet())
                        .in(EvaluationResultEntity::getEvaluatorVersionId, evaluatorVersionIds));
        // 按caseAttempt+evaluatorVersion分组，取最新一条评估结果（支持重试）
        Map<ResultKey, EvaluationResultEntity> latest = new HashMap<>();
        for (EvaluationResultEntity result : results) {
            ResultKey key = new ResultKey(result.getCaseAttemptId(), result.getEvaluatorVersionId());
            latest.merge(key, result, EvaluationMetricQueryService::latestResult);
        }
        // 遍历最新评估结果，填充评估器报错、跳过原因
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
     * 基础指标查询：从数据库读取原始指标实体
     * <p>限制最大返回行数，防止超大结果集拖垮内存；超限直接抛异常提示用户缩小过滤条件。
     * 注意：这里不是分页，是全量匹配+limit上限；上层search方法在内存里做切片分页。
     * </p>
     * @param spaceId 空间ID
     * @param runIds runId过滤列表，null/空则不限制
     * @param testCaseVersionIds 用例版本ID过滤列表
     * @param metricKeys metricKey过滤列表
     * @param sources 指标来源过滤列表（字符串枚举名）
     * @return 符合条件的指标实体列表，不超过MAX_METRIC_QUERY_ROWS
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
        // 达到阈值，判定可能存在数据爆炸，拒绝查询
        if (metrics.size() >= MAX_METRIC_QUERY_ROWS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Metric 查询结果过多，请缩小过滤范围");
        }
        return metrics;
    }

    /**
     * 过滤有效指标集合
     * <p>两层过滤：
     * 1. 调用 MetricEffectiveProjection 通用投影规则，筛掉无效记录
     * 2. 额外过滤：已取消attempt产出的指标，但是【执行型来源 EXECUTION】指标例外，即使attempt取消也保留
     * </p>
     * @param metrics 原始指标实体列表
     * @return 过滤之后的有效指标集合
     */
    private List<EvaluationMetricEntity> effective(List<EvaluationMetricEntity> metrics) {
        if (metrics.isEmpty()) {
            return metrics;
        }
        // 收集所有关联的caseRunId
        Set<Long> caseRunIds = metrics.stream().map(EvaluationMetricEntity::getCaseRunId)
                .collect(Collectors.toSet());
        List<EvaluationCaseRunEntity> caseRuns = caseRunMapper.selectBatchIds(caseRunIds);
        Set<Long> currentAttemptIds = caseRuns.stream().map(EvaluationCaseRunEntity::getCurrentAttemptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        // 找出状态为取消/待取消的attemptId集合
        Set<Long> canceledAttemptIds = currentAttemptIds.isEmpty() ? Set.of()
                : attemptMapper.selectBatchIds(currentAttemptIds).stream()
                .filter(attempt -> EvaluationAttemptStatus.CANCELED.name().equals(attempt.getStatus())
                        || EvaluationAttemptStatus.CANCEL_PENDING.name().equals(attempt.getStatus()))
                .map(EvaluationCaseAttemptEntity::getId).collect(Collectors.toSet());
        // 收集评估器版本，用于查询评估结果给投影逻辑使用
        Set<Long> evaluatorVersionIds = metrics.stream().map(EvaluationMetricEntity::getEvaluatorVersionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<EvaluationResultEntity> results = currentAttemptIds.isEmpty() || evaluatorVersionIds.isEmpty()
                ? List.of()
                : resultMapper.selectList(new LambdaQueryWrapper<EvaluationResultEntity>()
                .in(EvaluationResultEntity::getCaseAttemptId, currentAttemptIds)
                .in(EvaluationResultEntity::getEvaluatorVersionId, evaluatorVersionIds));
        // 执行通用有效指标投影
        List<EvaluationMetricEntity> selected = MetricEffectiveProjection.select(metrics, caseRuns, results);
        // 二次过滤：取消attempt的指标剔除，EXECUTION来源除外
        return selected.stream().filter(metric -> !canceledAttemptIds.contains(metric.getCaseAttemptId())
                || EvaluationMetricSource.EXECUTION.name().equals(metric.getSource())).toList();
    }

    /**
     * 指标实体转VO，批量加载关联证据
     * <p>采用批量查询：先拿到metricId集合 → 查询关联中间表 → 查询证据引用主表，避免N+1
     * </p>
     * @param metrics 指标实体列表
     * @return 组装好证据信息的StandardMetricVO列表
     */
    private List<StandardMetricVO> toVO(List<EvaluationMetricEntity> metrics) {
        if (metrics.isEmpty()) {
            return List.of();
        }
        List<Long> metricIds = metrics.stream().map(EvaluationMetricEntity::getId).toList();
        // 查询指标与证据的关联关系
        List<EvaluationMetricEvidenceEntity> links = metricEvidenceMapper.selectList(
                new LambdaQueryWrapper<EvaluationMetricEvidenceEntity>()
                        .in(EvaluationMetricEvidenceEntity::getMetricId, metricIds));
        Set<Long> evidenceIds = links.stream().map(EvaluationMetricEvidenceEntity::getEvidenceReferenceId)
                .collect(Collectors.toSet());
        // 批量查证据引用主记录
        Map<Long, EvaluationEvidenceReferenceEntity> evidenceById = evidenceIds.isEmpty()
                ? Map.of()
                : evidenceMapper.selectBatchIds(evidenceIds).stream().collect(Collectors.toMap(
                EvaluationEvidenceReferenceEntity::getId, Function.identity()));
        // 按metricId分组，映射为证据VO列表
        Map<Long, List<MetricEvidenceReferenceVO>> evidenceByMetric = links.stream()
                .filter(link -> evidenceById.containsKey(link.getEvidenceReferenceId()))
                .collect(Collectors.groupingBy(EvaluationMetricEvidenceEntity::getMetricId,
                        Collectors.mapping(link -> evidenceVO(evidenceById.get(link.getEvidenceReferenceId())),
                                Collectors.toList())));
        // 实体转VO，绑定证据
        return metrics.stream().map(metric -> metricVO(metric,
                evidenceByMetric.getOrDefault(metric.getId(), List.of()))).toList();
    }

    /**
     * 校验待对比Run集合合法性
     * 所有run必须存在，并且全部归属同一个空间，否则无法对比
     * @param spaceId 当前操作空间ID
     * @param runIds 待对比runId列表
     */
    private void requireRuns(Long spaceId, List<Long> runIds) {
        List<EvaluationRunEntity> runs = runMapper.selectBatchIds(runIds);
        // 数量不一致说明存在不存在的run；或者存在不属于当前空间的run
        if (runs.size() != runIds.size() || runs.stream().anyMatch(run -> !spaceId.equals(run.getSpaceId()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "对比 Run 必须存在且属于同一空间");
        }
    }

    /**
     * 校验指标对比查询入参
     * <p>校验spaceId非空正整数；run数量在[2,MAX_COMPARISON_RUN_COUNT]之间；
     * runId不能为null或负数；各过滤列表长度不能超限；返回去重后有序runId列表
     * </p>
     * @param request 对比请求DTO
     * @return 去重后的runId列表，保持原有顺序
     */
    private static List<Long> validateComparisonRequest(MetricComparisonQueryDTO request) {
        if (request == null || request.spaceId() == null || request.spaceId() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "spaceId 必须为正整数");
        }
        // LinkedHashSet去重，保留原始传入顺序
        List<Long> runIds = request.runIds() == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(request.runIds()));
        // run数量校验：至少2个，不超过上限
        if (runIds.size() < 2 || runIds.size() > MAX_COMPARISON_RUN_COUNT
                || runIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "runIds 必须包含 2~" + MAX_COMPARISON_RUN_COUNT + " 个有效 ID");
        }
        // 校验过滤参数长度上限，防止传入超大IN列表
        requireFilterSize(request.testCaseVersionIds(), MAX_METRIC_QUERY_IDS, "testCaseVersionIds");
        requireFilterSize(request.metricKeys(), MAX_METRIC_QUERY_KEYS, "metricKeys");
        return runIds;
    }

    /**
     * 契约一致性校验
     * 同一个对齐键（用例版本+metricKey）下，所有run产出的指标契约元数据必须完全相同；
     * 契约版本、值类型、单位、优化方向、来源任一不一致，横向对比无意义，直接拒绝。
     * @param key 当前对齐分组key
     * @param expected 参考模板VO
     * @param metrics 本组全部指标VO
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
     * 指标排序规则
     * 排序优先级：runId → 用例版本ID → metricKey字符串 → 指标主键ID
     * 保证结果顺序稳定、可复现
     * @param metrics 待排序指标实体
     * @return 排序后不可变列表
     */
    private static List<EvaluationMetricEntity> sort(List<EvaluationMetricEntity> metrics) {
        return metrics.stream().sorted(Comparator.comparing(EvaluationMetricEntity::getRunId)
                .thenComparing(EvaluationMetricEntity::getTestCaseVersionId)
                .thenComparing(EvaluationMetricEntity::getMetricKey)
                .thenComparing(EvaluationMetricEntity::getId)).toList();
    }

    /**
     * EvaluationMetricEntity → StandardMetricVO
     * @param metric 数据库实体
     * @param evidence 关联证据VO列表
     * @return 视图对象
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
     * 证据引用实体 → MetricEvidenceReferenceVO
     * @param evidence 证据引用实体
     * @return 证据视图对象
     */
    private static MetricEvidenceReferenceVO evidenceVO(EvaluationEvidenceReferenceEntity evidence) {
        return new MetricEvidenceReferenceVO(evidence.getId(), evidence.getEvidenceType(), evidence.getBusinessId(),
                evidence.getContentHash(), evidence.getSummary(), evidence.getLocatorJson());
    }

    /**
     * 将枚举来源转为字符串名称，用于数据库in查询
     * @param param 搜索参数
     * @return 来源字符串列表
     */
    private static List<String> sourceNames(MetricSearchParam param) {
        return param.getSources() == null ? List.of()
                : param.getSources().stream().map(Enum::name).toList();
    }

    /**
     * 工具方法：判断集合不为null且非空
     * @param values 目标集合
     * @return true=非空
     */
    private static boolean notEmpty(List<?> values) {
        return values != null && !values.isEmpty();
    }

    /**
     * 校验过滤列表长度，防止IN子句参数过多
     * @param values 待校验集合
     * @param maxSize 最大允许条数
     * @param field 字段名称，用于报错提示
     */
    private static void requireFilterSize(List<?> values, int maxSize, String field) {
        if (values != null && values.size() > maxSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 最多允许 " + maxSize + " 项");
        }
    }

    /**
     * 评估结果取最新记录的合并函数
     * 优先按 evaluationAttemptNo（评估重试次数）；重试号相同时，取主键ID更大的记录
     * @param left 旧记录
     * @param right 新记录候选
     * @return 时间/重试维度更新的那条记录
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
     * 指标对齐分组Key
     * 唯一标识一条对比行：用例版本ID + metricKey
     */
    private record AlignmentKey(Long testCaseVersionId, String metricKey) { }

    /**
     * 评估结果分组Key
     * caseAttemptId + evaluatorVersionId，定位一次评估器执行记录
     */
    private record ResultKey(Long caseAttemptId, Long evaluatorVersionId) { }

    /**
     * 缺失原因索引Key
     * runId + 用例版本ID + evaluatorVersionId，唯一定位“哪个run下哪个评估器在哪个用例版本没有产出指标”
     */
    private record MissingKey(Long runId, Long testCaseVersionId, Long evaluatorVersionId) { }
}
