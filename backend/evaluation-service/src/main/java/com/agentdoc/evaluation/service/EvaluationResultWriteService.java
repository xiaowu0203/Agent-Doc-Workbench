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
 * <p>事务内一次性写入评估结果主记录、证据引用、指标、指标-证据关联中间表；
 * 前置多层强校验：身份归属、状态约束、字段长度、JSON格式合法性、业务规则约束，
 * 保证一组评估产出数据要么全部落库，要么全部回滚，维持数据一致性。
 * 职责边界：只负责单轮评估结果的追加写入，不支持更新、删除、部分修改。
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
     * <p>事务原子写入流程：
     * 1. 校验写入身份、跨表外键归属、评估器版本发布状态、Attempt可写状态
     * 2. 校验命令参数合法性、业务规则约束（状态与指标的互斥/强制关系）
     * 3. 构造评估结果主实体、证据引用实体Map、指标实体列表
     * 4. 依次插入 result → evidence → metric
     * 5. 遍历指标，建立指标与证据的关联中间表记录（通过command内定义的referenceKey映射）
     * 6. 返回本次写入的主键ID集合
     * </p>
     * @param command 评估器产出写入命令，包含结果、指标、证据的完整输入模型
     * @return 写入结果VO，携带评估结果ID、指标ID列表、证据ID列表
     */
    @Transactional
    public EvaluationResultWriteVO append(EvaluatorResultWriteCommand command) {
        // 校验写入身份与跨实体归属一致性
        WriteIdentity identity = requireIdentity(command);
        // 校验命令字段与业务规则
        validateCommand(command);
        // 组装评估结果主实体
        EvaluationResultEntity result = result(command, identity.evaluatorVersion());
        // 构建证据引用实体，key为命令内的本地引用键，用于后续关联
        Map<String, EvaluationEvidenceReferenceEntity> evidence = buildEvidence(command, result.getId());
        // 构建指标实体，校验指标key重复、证据引用合法性
        List<EvaluationMetricEntity> metrics = buildMetrics(command, identity, result.getId(), evidence.keySet());

        // 主记录先入库，生成result主键ID，供子记录外键使用
        resultMapper.insert(result);
        // 批量插入证据引用记录
        evidence.values().forEach(evidenceMapper::insert);
        // 批量插入指标记录
        metrics.forEach(metricMapper::insert);

        // 建立 referenceKey -> 数据库自增ID映射，用于构造关联记录
        Map<String, Long> evidenceIds = new HashMap<>();
        evidence.forEach((key, value) -> evidenceIds.put(key, value.getId()));

        // 逐个创建【指标-证据】关联中间表记录
        for (int index = 0; index < metrics.size(); index++) {
            EvaluationMetricEntity metric = metrics.get(index);
            StandardMetricOutput output = command.metrics().get(index);
            // 遍历当前指标绑定的证据引用key，生成关联
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
     * 校验写入身份与跨表归属关系
     * <p>校验项：
     * 1. 入参所有必填外键字段非空
     * 2. CaseAttempt、CaseRun、EvaluatorVersion记录存在
     * 3. 多表之间 spaceId / runId / caseRunId / caseAttemptId / testCaseVersionId 完全一致，防止跨空间跨任务脏写入
     * 4. 评估器版本状态必须为已发布 PUBLISHED，禁止使用草稿版本执行评估写入
     * 5. CaseAttempt 状态必须是 EVALUATING，仅评估中状态允许追加评估结果
     * </p>
     * @param command 写入命令
     * @return 封装好的本次写入身份上下文 WriteIdentity
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

        // 校验实体存在性 + 所有关联ID归属一致
        if (attempt == null || caseRun == null || evaluatorVersion == null
                || !command.runId().equals(attempt.getRunId()) || !command.spaceId().equals(attempt.getSpaceId())
                || !command.caseRunId().equals(attempt.getCaseRunId())
                || !command.runId().equals(caseRun.getRunId()) || !command.spaceId().equals(caseRun.getSpaceId())
                || !command.testCaseVersionId().equals(caseRun.getTestCaseVersionId())
                || !command.spaceId().equals(evaluatorVersion.getSpaceId())
                || !EvaluationVersionStatus.PUBLISHED.name().equals(evaluatorVersion.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "EvaluationResult 写入身份或版本归属不一致");
        }
        // 只有评估进行中状态才允许写入评估结果
        if (!EvaluationAttemptStatus.EVALUATING.name().equals(attempt.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "CaseAttempt 当前不允许追加评价结果");
        }
        return new WriteIdentity(attempt, caseRun, evaluatorVersion);
    }

    /**
     * 校验写入命令参数合法性与业务约束
     * <p>业务强约束：
     * <ul>
     * <li>ERROR / SKIPPED：评估失败/跳过，**禁止携带任何指标**</li>
     * <li>PASSED / FAILED：评估正常结束，**必须至少有一条指标**</li>
     * </ul>
     * 同时校验轮次号、时间顺序、字段长度、JSON对象格式。
     * </p>
     * @param command 写入命令
     */
    private void validateCommand(EvaluatorResultWriteCommand command) {
        if (command.evaluationAttemptNo() == null || command.evaluationAttemptNo() < 1
                || command.status() == null || command.summaryCode() == null
                || command.summaryCode().isBlank() || command.summaryCode().length() > MAX_SUMMARY_CODE_LENGTH
                || command.startedAt() == null || command.finishedAt() == null
                || command.finishedAt().isBefore(command.startedAt())) {
            throw invalid("EvaluationResult 内容无效");
        }
        // detailsJson 必须是合法JSON对象（数组、原始值不接受）
        validateJsonObject(command.detailsJson(), "detailsJson");
        List<StandardMetricOutput> metrics = safeList(command.metrics());
        // 异常状态不能产出指标
        if ((command.status() == EvaluationResultStatus.ERROR || command.status() == EvaluationResultStatus.SKIPPED)
                && !metrics.isEmpty()) {
            throw invalid("ERROR/SKIPPED Result 不允许产生质量 Metric");
        }
        // 正常评估结束必须产出指标
        if ((command.status() == EvaluationResultStatus.PASSED || command.status() == EvaluationResultStatus.FAILED)
                && metrics.isEmpty()) {
            throw invalid("PASSED/FAILED Result 必须产生标准 Metric");
        }
    }

    /**
     * 构建证据引用实体Map
     * <p>使用 LinkedHashMap 保证顺序；key为命令传入的 referenceKey，是本次写入域内的本地临时主键，
     * 后续指标通过该key引用证据；校验证据字段长度、哈希格式、locatorJson合法性、key不可重复。
     * </p>
     * @param command 写入命令
     * @param resultId 评估结果主键ID（已预先生成）
     * @return referenceKey -> EvaluationEvidenceReferenceEntity
     */
    private Map<String, EvaluationEvidenceReferenceEntity> buildEvidence(EvaluatorResultWriteCommand command,
                                                                         Long resultId) {
        Map<String, EvaluationEvidenceReferenceEntity> values = new java.util.LinkedHashMap<>();
        for (EvidenceReferenceValue value : safeList(command.evidence())) {
            if (value == null || value.referenceKey() == null || value.referenceKey().isBlank()
                    || value.evidenceType() == null || value.businessId() == null || value.businessId().isBlank()
                    || value.businessId().length() > MAX_EVIDENCE_BUSINESS_ID_LENGTH
                    // contentHash 为sha256，固定64位十六进制
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
     * <p>校验：
     * 1. 同一次写入内 metricKey 不可重复
     * 2. 指标引用的所有 evidenceReferenceKeys 必须在当前批次证据key集合中存在（提前校验，避免外键不存在）
     * 使用 MetricFactory 工厂方法完成指标实体构造，统一指标生产逻辑。
     * </p>
     * @param command 写入命令
     * @param identity 写入身份上下文
     * @param resultId 评估结果ID
     * @param evidenceKeys 当前批次所有证据本地引用key集合
     * @return 指标实体列表
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
     * 组装EvaluationResult主实体，填充基础字段；
     * implementationVersion 从评估器版本实体继承，不从前端命令传入，防止篡改。
     * @param command 写入命令
     * @param evaluatorVersion 评估器版本实体
     * @return 待入库的评估结果实体
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
        // 实现版本取自评估器版本配置，不可由调用方覆盖
        result.setImplementationVersion(evaluatorVersion.getImplementationVersion());
        result.setTraceId(command.traceId());
        result.setSpanId(command.spanId());
        result.setStartedAt(command.startedAt());
        result.setFinishedAt(command.finishedAt());
        return result;
    }

    /**
     * 校验字符串是合法JSON对象（{}）；null/空白直接放行
     * 不接受JSON数组、数字、字符串等非对象类型
     * @param json 待校验JSON串
     * @param field 字段名，用于报错提示
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
     * 集合安全包装，null转为空不可变List，避免空指针
     * @param values 原始列表，可为null
     * @param <T> 元素类型
     * @return 非null列表
     */
    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 快速构造 BAD_REQUEST 业务异常
     * @param message 错误信息
     * @return BusinessException
     */
    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    /**
     * 写入身份载体 record
     * 封装本次写入绑定的 CaseAttempt、CaseRun、EvaluatorVersion，减少多次Mapper查询与参数传递
     * @param attempt 用例尝试记录
     * @param caseRun 用例运行记录
     * @param evaluatorVersion 评估器版本记录
     */
    private record WriteIdentity(EvaluationCaseAttemptEntity attempt,
                                 EvaluationCaseRunEntity caseRun,
                                 EvaluatorVersionEntity evaluatorVersion) {
    }
}
