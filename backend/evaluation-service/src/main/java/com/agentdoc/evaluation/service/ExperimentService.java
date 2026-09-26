package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.dto.AgentCandidateConfigCreateDTO;
import com.agentdoc.common.feign.dto.ExperimentBatchCreateDTO;
import com.agentdoc.common.feign.dto.ExperimentBatchItemDTO;
import com.agentdoc.common.feign.dto.ReplayBatchCreateDTO;
import com.agentdoc.common.feign.dto.ReplayBatchItemDTO;
import com.agentdoc.common.feign.vo.AgentCandidateConfigVO;
import com.agentdoc.common.feign.vo.ExperimentBatchCreateVO;
import com.agentdoc.common.feign.vo.ReplayBatchCreateVO;
import com.agentdoc.common.feign.vo.ReplaySourceVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.constant.EvaluationConstant;
import com.agentdoc.evaluation.enums.EvaluationRunStatus;
import com.agentdoc.evaluation.enums.EvaluationVersionStatus;
import com.agentdoc.evaluation.enums.ExperimentStatus;
import com.agentdoc.evaluation.enums.ExperimentVariantRole;
import com.agentdoc.evaluation.enums.ExperimentVariantType;
import com.agentdoc.evaluation.mapper.EvaluationDatasetCaseMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.mapper.ExperimentMapper;
import com.agentdoc.evaluation.mapper.ExperimentVariantMapper;
import com.agentdoc.evaluation.mapper.TestCaseEvaluatorMapper;
import com.agentdoc.evaluation.metric.MetricDefinition;
import com.agentdoc.evaluation.metric.MetricDefinitionCatalog;
import com.agentdoc.evaluation.pojo.dto.ExperimentCreateDTO;
import com.agentdoc.evaluation.pojo.dto.ExperimentStartDTO;
import com.agentdoc.evaluation.pojo.dto.PromptCandidateCreateDTO;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetCaseEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentVariantEntity;
import com.agentdoc.evaluation.pojo.entity.TestCaseEvaluatorEntity;
import com.agentdoc.evaluation.pojo.vo.ExperimentPreflightIssueVO;
import com.agentdoc.evaluation.pojo.vo.ExperimentPreflightVO;
import com.agentdoc.evaluation.pojo.vo.ExperimentVO;
import com.agentdoc.evaluation.pojo.vo.ExperimentVariantVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.AGENT_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_RUN;

/** 离线 Experiment 创建、预检、启动、恢复和取消编排。 */
@Service
@RequiredArgsConstructor
public class ExperimentService {

    private static final int MANIFEST_SCHEMA_VERSION = 1;
    private static final int REQUEST_SCHEMA_VERSION = 1;

    private final ExperimentMapper experimentMapper;
    private final ExperimentVariantMapper variantMapper;
    private final EvaluationDatasetVersionMapper datasetVersionMapper;
    private final EvaluationDatasetMapper datasetMapper;
    private final EvaluationDatasetCaseMapper datasetCaseMapper;
    private final EvaluationTestCaseVersionMapper testCaseVersionMapper;
    private final EvaluationTestCaseMapper testCaseMapper;
    private final TestCaseEvaluatorMapper testCaseEvaluatorMapper;
    private final EvaluatorVersionMapper evaluatorVersionMapper;
    private final EvaluationRunMapper runMapper;
    private final ExperimentPersistenceService persistenceService;
    private final EvaluationRunPersistenceService runPersistenceService;
    private final EvaluationRunService evaluationRunService;
    private final WorkerCapabilitySegmentService segmentService;
    private final SpaceAccessService spaceAccessService;
    private final TaskFeign taskFeign;
    private final AgentFeign agentFeign;

    public ExperimentVO create(ExperimentCreateDTO request) {
        requireCreateRequest(request);
        spaceAccessService.requirePermission(request.spaceId(), EVALUATION_MANAGE);
        spaceAccessService.requirePermission(request.spaceId(), AGENT_MANAGE);
        String requestHash = StableSnapshotUtils.snapshotHash(REQUEST_SCHEMA_VERSION,
                new CreateRequest(request.spaceId(), request.datasetVersionId(), request.candidateVariants()));
        ExperimentEntity existing = findByRequestKey(request.spaceId(), request.clientRequestKey());
        if (existing != null) {
            requireSameRequest(existing, requestHash);
            return detailInternal(existing);
        }

        ManifestBuild manifestBuild = buildManifest(request.spaceId(), request.datasetVersionId());
        ReplaySourceVO representative = manifestBuild.sources().getFirst();
        List<CandidateDraft> candidates = new ArrayList<>();
        for (PromptCandidateCreateDTO candidate : request.candidateVariants()) {
            AgentCandidateConfigVO identity = requireData(agentFeign.createCandidateConfig(
                    new AgentCandidateConfigCreateDTO(
                            "experiment:" + request.clientRequestKey() + ":candidate:" + candidate.variantKey(),
                            request.spaceId(), representative.sourceTaskId(), representative.sourceExecutionId(),
                            representative.executionSnapshotSchemaVersion(),
                            representative.executionSnapshotHash(), candidate.agentPrompt())));
            if (!Objects.equals(identity.agentId(), representative.agentId())
                    || !Objects.equals(identity.sourceSnapshotHash(),
                    representative.executionSnapshotHash())) {
                throw new BusinessException(ErrorCode.CONFLICT, "CANDIDATE_CONFIG_INVALID");
            }
            candidates.add(new CandidateDraft(candidate.variantKey(), identity));
        }

        ExperimentEntity experiment = new ExperimentEntity();
        experiment.setId(IdWorker.getId());
        experiment.setSpaceId(request.spaceId());
        experiment.setDatasetVersionId(request.datasetVersionId());
        experiment.setStatus(ExperimentStatus.CREATED.name());
        experiment.setClientRequestKey(request.clientRequestKey());
        experiment.setRequestHash(requestHash);
        experiment.setManifestSchemaVersion(MANIFEST_SCHEMA_VERSION);
        experiment.setManifestJson(JsonUtils.toJson(manifestBuild.manifest()));
        experiment.setManifestHash(StableSnapshotUtils.snapshotHash(MANIFEST_SCHEMA_VERSION,
                manifestBuild.manifest()));
        experiment.setCreatedBy(AuthUtils.getUserIdOrException());

        List<ExperimentVariantEntity> variants = new ArrayList<>();
        AgentCandidateConfigVO proof = candidates.getFirst().identity();
        variants.add(variant(experiment, "baseline", ExperimentVariantRole.BASELINE, null,
                proof.sourceSnapshotSchemaVersion(), proof.sourceSnapshotHash(),
                proof.sourceSnapshotSchemaVersion(), proof.sourceSnapshotHash(), List.of(),
                proof.snapshotWithoutPromptHash()));
        for (CandidateDraft candidate : candidates) {
            AgentCandidateConfigVO identity = candidate.identity();
            variants.add(variant(experiment, candidate.variantKey(), ExperimentVariantRole.CANDIDATE,
                    identity.candidateConfigId(), identity.sourceSnapshotSchemaVersion(),
                    identity.sourceSnapshotHash(), identity.candidateSnapshotSchemaVersion(),
                    identity.candidateSnapshotHash(), identity.promptDiffFieldPaths(),
                    identity.snapshotWithoutPromptHash()));
        }
        try {
            persistenceService.create(experiment, variants);
        } catch (DuplicateKeyException exception) {
            ExperimentEntity concurrent = findByRequestKey(request.spaceId(), request.clientRequestKey());
            if (concurrent == null) {
                throw exception;
            }
            requireSameRequest(concurrent, requestHash);
            return detailInternal(concurrent);
        }
        return toVO(experiment, variants);
    }

    public ExperimentVO detail(Long id) {
        ExperimentEntity experiment = requireExperiment(id);
        spaceAccessService.requirePermission(experiment.getSpaceId(), EVALUATION_READ);
        reconcile(experiment);
        return detailInternal(requireExperiment(id));
    }

    public List<ExperimentVariantVO> variants(Long id) {
        ExperimentEntity experiment = requireExperiment(id);
        spaceAccessService.requirePermission(experiment.getSpaceId(), EVALUATION_READ);
        return variantsOf(id).stream().map(this::toVariantVO).toList();
    }

    public ExperimentPreflightVO preflight(Long id) {
        ExperimentEntity experiment = requireExperiment(id);
        spaceAccessService.requirePermission(experiment.getSpaceId(), EVALUATION_READ);
        return preflightInternal(experiment, variantsOf(id));
    }

    public ExperimentVO start(Long id, ExperimentStartDTO request) {
        ExperimentEntity experiment = requireExperiment(id);
        spaceAccessService.requirePermission(experiment.getSpaceId(), EVALUATION_RUN);
        if (experiment.getAuthorizedTokenBudget() != null
                && !Objects.equals(experiment.getAuthorizedTokenBudget(), request.authorizedTokenBudget())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Experiment 已使用不同预算确认启动");
        }
        ExperimentStatus status = ExperimentStatus.valueOf(experiment.getStatus());
        if (status.terminal() || status == ExperimentStatus.CANCEL_PENDING) {
            return detailInternal(experiment);
        }
        if (status == ExperimentStatus.STARTING || status == ExperimentStatus.RUNNING) {
            return detailInternal(experiment);
        }
        List<ExperimentVariantEntity> variants = variantsOf(id);
        ExperimentPreflightVO preflight = preflightInternal(experiment, variants);
        if (!preflight.eligible()) {
            throw new BusinessException(ErrorCode.CONFLICT, "SOURCE_NOT_REPLAYABLE");
        }
        if (request.authorizedTokenBudget() < preflight.plannedTokenBudget()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "BUDGET_LIMIT_EXCEEDED");
        }
        if (status == ExperimentStatus.CREATED || status == ExperimentStatus.PAUSED) {
            boolean accepted = persistenceService.acceptStart(id, AuthUtils.getUserIdOrException(),
                    request.authorizedTokenBudget());
            if (!accepted) {
                experiment = requireExperiment(id);
                if (!Objects.equals(experiment.getAuthorizedTokenBudget(), request.authorizedTokenBudget())) {
                    throw new BusinessException(ErrorCode.CONFLICT, "Experiment 已被并发启动");
                }
                return detailInternal(experiment);
            }
            experiment = requireExperiment(id);
        }
        try {
            dispatch(experiment, variants, request.workerCapabilityTtlSeconds());
            persistenceService.updateStatus(id, ExperimentStatus.RUNNING, null, null);
        } catch (RuntimeException exception) {
            persistenceService.updateStatus(id, ExperimentStatus.PAUSED, "DISPATCH_UNAVAILABLE",
                    "Experiment 派发暂时不可用，可使用相同预算恢复");
        }
        return detailInternal(requireExperiment(id));
    }

    public ExperimentVO cancel(Long id) {
        ExperimentEntity experiment = requireExperiment(id);
        spaceAccessService.requirePermission(experiment.getSpaceId(), EVALUATION_RUN);
        ExperimentStatus status = ExperimentStatus.valueOf(experiment.getStatus());
        if (status == ExperimentStatus.CANCELED || status == ExperimentStatus.CANCEL_PENDING) {
            return detailInternal(experiment);
        }
        if (status.terminal()) {
            throw new BusinessException(ErrorCode.CONFLICT, "终态 Experiment 不能取消");
        }
        persistenceService.requestCancel(id, AuthUtils.getUserIdOrException());
        boolean pending = false;
        for (ExperimentVariantEntity variant : variantsOf(id)) {
            if (variant.getEvaluationRunId() == null) {
                continue;
            }
            EvaluationRunEntity run = runMapper.selectById(variant.getEvaluationRunId());
            if (run != null && !EvaluationRunStatus.valueOf(run.getStatus()).terminal()) {
                try {
                    evaluationRunService.cancel(run.getId());
                    pending = true;
                } catch (RuntimeException exception) {
                    pending = true;
                }
            }
        }
        if (!pending) {
            persistenceService.updateStatus(id, ExperimentStatus.CANCELED, "CANCEL_REQUESTED", null);
        }
        return detailInternal(requireExperiment(id));
    }

    private void dispatch(ExperimentEntity experiment, List<ExperimentVariantEntity> variants, Long ttlSeconds) {
        ExperimentManifest manifest = requireManifest(experiment);
        Map<Long, ManifestCase> cases = manifest.cases().stream()
                .collect(Collectors.toMap(ManifestCase::testCaseVersionId, Function.identity()));
        List<Long> caseIds = manifest.cases().stream().map(ManifestCase::testCaseVersionId).toList();
        for (ExperimentVariantEntity variant : variants) {
            EvaluationRunPersistenceService.RunDraft draft = runPersistenceService.createForExperiment(
                    experiment.getSpaceId(), experiment.getDatasetVersionId(), variant.getId(),
                    experiment.getStartedBy(), caseIds);
            persistenceService.linkRun(variant.getId(), draft.run().getId());
            if (EvaluationRunStatus.RUNNING.name().equals(draft.run().getStatus())
                    || EvaluationRunStatus.valueOf(draft.run().getStatus()).terminal()) {
                continue;
            }
            Map<Long, String> keys = draft.cases().stream().collect(Collectors.toMap(
                    item -> item.attempt().getId(), item -> derivationKey(experiment.getId(), variant.getId(),
                            item.caseRun().getTestCaseVersionId(), item.attempt().getAttemptNo())));
            int batchNo = segmentService.nextBatchNo(draft.run().getId());
            if (ExperimentVariantRole.BASELINE.name().equals(variant.getRole())) {
                List<ReplayBatchItemDTO> items = draft.cases().stream()
                        .map(item -> new ReplayBatchItemDTO(
                                cases.get(item.caseRun().getTestCaseVersionId()).sourceTaskId(),
                                keys.get(item.attempt().getId()), variant.getId(),
                                item.caseRun().getTestCaseVersionId(), item.attempt().getAttemptNo()))
                        .toList();
                ReplayBatchCreateVO response = requireData(taskFeign.createReplayBatch(new ReplayBatchCreateDTO(
                        draft.run().getId(), experiment.getSpaceId(), ttlSeconds, items)));
                runPersistenceService.attachDispatch(draft, response, batchNo, keys);
            } else {
                List<ExperimentBatchItemDTO> items = draft.cases().stream()
                        .map(item -> new ExperimentBatchItemDTO(
                                cases.get(item.caseRun().getTestCaseVersionId()).sourceTaskId(),
                                item.caseRun().getTestCaseVersionId(), item.attempt().getAttemptNo(),
                                keys.get(item.attempt().getId())))
                        .toList();
                ExperimentBatchCreateVO response = requireData(taskFeign.createExperimentBatch(
                        new ExperimentBatchCreateDTO(draft.run().getId(), variant.getId(),
                                experiment.getSpaceId(), variant.getCandidateConfigId(),
                                variant.getCandidateSnapshotSchemaVersion(), variant.getCandidateSnapshotHash(),
                                ttlSeconds, items)));
                runPersistenceService.attachExperimentDispatch(draft, response, keys, batchNo);
            }
        }
    }

    private ExperimentPreflightVO preflightInternal(ExperimentEntity experiment,
                                                     List<ExperimentVariantEntity> variants) {
        ExperimentManifest manifest = requireManifest(experiment);
        List<ExperimentPreflightIssueVO> issues = new ArrayList<>();
        long tokenBudget = 0L;
        for (ManifestCase item : manifest.cases()) {
            try {
                ReplaySourceVO source = requireData(taskFeign.getReplaySource(
                        item.sourceTaskId(), experiment.getSpaceId()));
                if (!source.replayable()) {
                    issues.add(new ExperimentPreflightIssueVO(item.testCaseVersionId(),
                            "SOURCE_NOT_REPLAYABLE", source.reasonCode()));
                }
                if (!Objects.equals(source.sourceExecutionId(), item.sourceExecutionId())
                        || !Objects.equals(source.inputSnapshotHash(), item.inputSnapshotHash())
                        || !Objects.equals(source.executionSnapshotHash(), manifest.sourceSnapshotHash())) {
                    issues.add(new ExperimentPreflightIssueVO(item.testCaseVersionId(),
                            "MANIFEST_MISMATCH", null));
                }
                tokenBudget = Math.addExact(tokenBudget, Objects.requireNonNullElse(source.tokenBudget(), 0L));
            } catch (RuntimeException exception) {
                issues.add(new ExperimentPreflightIssueVO(item.testCaseVersionId(),
                        "SOURCE_NOT_REPLAYABLE", "SOURCE_QUERY_FAILED"));
            }
        }
        for (ExperimentVariantEntity variant : variants) {
            if (variant.getCandidateConfigId() == null) {
                continue;
            }
            try {
                AgentCandidateConfigVO identity = requireData(agentFeign.getCandidateConfigIdentity(
                        variant.getCandidateConfigId(), experiment.getSpaceId(),
                        variant.getCandidateSnapshotHash()));
                if (!Objects.equals(identity.sourceSnapshotHash(), manifest.sourceSnapshotHash())) {
                    throw new BusinessException(ErrorCode.CONFLICT, "候选来源不一致");
                }
            } catch (RuntimeException exception) {
                issues.add(new ExperimentPreflightIssueVO(null, "CANDIDATE_CONFIG_INVALID", null));
            }
        }
        long planned = Math.multiplyExact(tokenBudget, variants.size());
        return new ExperimentPreflightVO(experiment.getId(), manifest.cases().size(), variants.size(),
                (long) manifest.cases().size() * variants.size(), planned, issues.isEmpty(), issues);
    }

    private ManifestBuild buildManifest(Long spaceId, Long datasetVersionId) {
        EvaluationDatasetVersionEntity datasetVersion = datasetVersionMapper.selectById(datasetVersionId);
        EvaluationDatasetEntity dataset = datasetVersion == null ? null
                : datasetMapper.selectById(datasetVersion.getDatasetId());
        if (datasetVersion == null || !Objects.equals(spaceId, datasetVersion.getSpaceId())
                || !EvaluationVersionStatus.PUBLISHED.name().equals(datasetVersion.getStatus())
                || dataset == null || Boolean.TRUE.equals(dataset.getArchived())) {
            throw new BusinessException(ErrorCode.CONFLICT, "DATASET_VERSION_NOT_PUBLISHED");
        }
        List<EvaluationDatasetCaseEntity> bindings = datasetCaseMapper.selectList(
                new LambdaQueryWrapper<EvaluationDatasetCaseEntity>()
                        .eq(EvaluationDatasetCaseEntity::getDatasetVersionId, datasetVersionId)
                        .eq(EvaluationDatasetCaseEntity::getEnabled, true)
                        .orderByAsc(EvaluationDatasetCaseEntity::getSortOrder,
                                EvaluationDatasetCaseEntity::getId));
        if (bindings.isEmpty() || bindings.size() > EvaluationConstant.MAX_DATASET_CASE_COUNT) {
            throw new BusinessException(ErrorCode.CONFLICT, "DatasetVersion 启用用例数量无效");
        }
        Map<Long, EvaluationTestCaseVersionEntity> versions = testCaseVersionMapper.selectBatchIds(
                        bindings.stream().map(EvaluationDatasetCaseEntity::getTestCaseVersionId).toList())
                .stream().collect(Collectors.toMap(EvaluationTestCaseVersionEntity::getId, Function.identity()));
        List<String> errors = new ArrayList<>();
        List<ReplaySourceVO> sources = new ArrayList<>();
        List<ManifestCase> cases = new ArrayList<>();
        Long commonAgentId = null;
        String commonSnapshotHash = null;
        for (EvaluationDatasetCaseEntity binding : bindings) {
            Long caseVersionId = binding.getTestCaseVersionId();
            EvaluationTestCaseVersionEntity version = versions.get(caseVersionId);
            EvaluationTestCaseEntity testCase = version == null ? null : testCaseMapper.selectById(version.getTestCaseId());
            if (version == null || testCase == null || Boolean.TRUE.equals(testCase.getArchived())
                    || !Objects.equals(spaceId, version.getSpaceId())
                    || !EvaluationVersionStatus.PUBLISHED.name().equals(version.getStatus())) {
                errors.add(caseVersionId + ":CASE_SPACE_MISMATCH");
                continue;
            }
            ReplaySourceVO source;
            try {
                source = requireData(taskFeign.getReplaySource(version.getSourceTaskId(), spaceId));
            } catch (RuntimeException exception) {
                errors.add(caseVersionId + ":SOURCE_INPUT_IDENTITY_MISMATCH");
                continue;
            }
            if (source.agentId() == null || source.sourceExecutionId() == null
                    || source.inputSnapshotSchemaVersion() == null || source.inputSnapshotHash() == null
                    || source.executionSnapshotSchemaVersion() == null
                    || source.executionSnapshotSchemaVersion() != 3 || source.executionSnapshotHash() == null
                    || source.documentId() == null || source.documentVersionSnapshot() == null
                    || source.documentContentSha256() == null) {
                errors.add(caseVersionId + ":SOURCE_INPUT_IDENTITY_MISMATCH");
                continue;
            }
            if (commonAgentId != null && !Objects.equals(commonAgentId, source.agentId())) {
                errors.add(caseVersionId + ":CASE_AGENT_MISMATCH");
            }
            if (commonSnapshotHash != null
                    && !Objects.equals(commonSnapshotHash, source.executionSnapshotHash())) {
                errors.add(caseVersionId + ":SOURCE_SNAPSHOT_HASH_MISMATCH");
            }
            commonAgentId = commonAgentId == null ? source.agentId() : commonAgentId;
            commonSnapshotHash = commonSnapshotHash == null ? source.executionSnapshotHash() : commonSnapshotHash;
            List<TestCaseEvaluatorEntity> evaluatorBindings = testCaseEvaluatorMapper.selectList(
                    new LambdaQueryWrapper<TestCaseEvaluatorEntity>()
                            .eq(TestCaseEvaluatorEntity::getTestCaseVersionId, caseVersionId)
                            .orderByAsc(TestCaseEvaluatorEntity::getSortOrder, TestCaseEvaluatorEntity::getId));
            Map<Long, EvaluatorVersionEntity> evaluatorVersions = evaluatorVersionMapper.selectBatchIds(
                            evaluatorBindings.stream().map(TestCaseEvaluatorEntity::getEvaluatorVersionId).toList())
                    .stream().collect(Collectors.toMap(EvaluatorVersionEntity::getId, Function.identity()));
            List<ManifestEvaluator> evaluators = evaluatorBindings.stream().map(item -> {
                EvaluatorVersionEntity evaluator = evaluatorVersions.get(item.getEvaluatorVersionId());
                if (evaluator == null || !Objects.equals(spaceId, evaluator.getSpaceId())
                        || !EvaluationVersionStatus.PUBLISHED.name().equals(evaluator.getStatus())
                        || evaluator.getContentHash() == null) {
                    errors.add(caseVersionId + ":EVALUATOR_BINDING_INVALID");
                    return null;
                }
                return new ManifestEvaluator(evaluator.getId(), evaluator.getEvaluatorKey(),
                        evaluator.getContentHash());
            }).filter(Objects::nonNull).toList();
            if (evaluatorBindings.isEmpty()) {
                errors.add(caseVersionId + ":EVALUATOR_BINDING_INVALID");
            }
            sources.add(source);
            cases.add(new ManifestCase(caseVersionId, source.sourceTaskId(), source.sourceExecutionId(),
                    source.inputSnapshotSchemaVersion(), source.inputSnapshotHash(), source.documentId(),
                    source.documentVersionSnapshot(), source.documentContentSha256(), evaluators));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, String.join(",", errors));
        }
        List<MetricManifest> metrics = MetricDefinitionCatalog.definitions().stream()
                .map(MetricManifest::from).toList();
        String metricHash = StableSnapshotUtils.snapshotHash(1, metrics);
        ExperimentManifest manifest = new ExperimentManifest(datasetVersionId, cases, 3,
                commonSnapshotHash, 1, metrics, metricHash);
        return new ManifestBuild(manifest, sources);
    }

    private void requireCreateRequest(ExperimentCreateDTO request) {
        if (request == null || request.spaceId() == null || request.datasetVersionId() == null
                || request.clientRequestKey() == null || request.clientRequestKey().isBlank()
                || request.candidateVariants() == null || request.candidateVariants().isEmpty()
                || request.candidateVariants().size() > 19) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Experiment 创建参数无效");
        }
        Set<String> keys = new HashSet<>();
        for (PromptCandidateCreateDTO candidate : request.candidateVariants()) {
            if (candidate == null || candidate.variantKey() == null || candidate.variantKey().isBlank()
                    || candidate.agentPrompt() == null || candidate.agentPrompt().isBlank()
                    || "baseline".equalsIgnoreCase(candidate.variantKey()) || !keys.add(candidate.variantKey())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Experiment Variant key 无效或重复");
            }
        }
    }

    private ExperimentVariantEntity variant(ExperimentEntity experiment, String key,
                                             ExperimentVariantRole role, Long candidateConfigId,
                                             Integer sourceSchema, String sourceHash,
                                             Integer candidateSchema, String candidateHash,
                                             List<String> diffPaths, String withoutPromptHash) {
        ExperimentVariantEntity variant = new ExperimentVariantEntity();
        variant.setId(IdWorker.getId());
        variant.setExperimentId(experiment.getId());
        variant.setSpaceId(experiment.getSpaceId());
        variant.setVariantKey(key);
        variant.setRole(role.name());
        variant.setVariantType(ExperimentVariantType.PROMPT.name());
        variant.setCandidateConfigId(candidateConfigId);
        variant.setSourceSnapshotSchemaVersion(sourceSchema);
        variant.setSourceSnapshotHash(sourceHash);
        variant.setCandidateSnapshotSchemaVersion(candidateSchema);
        variant.setCandidateSnapshotHash(candidateHash);
        variant.setPromptDiffFieldPaths(JsonUtils.toJson(diffPaths));
        variant.setSnapshotWithoutPromptHash(withoutPromptHash);
        return variant;
    }

    private ExperimentManifest requireManifest(ExperimentEntity experiment) {
        ExperimentManifest manifest = JsonUtils.parse(experiment.getManifestJson(), ExperimentManifest.class);
        if (manifest == null || experiment.getManifestSchemaVersion() != MANIFEST_SCHEMA_VERSION
                || !Objects.equals(experiment.getManifestHash(),
                StableSnapshotUtils.snapshotHash(MANIFEST_SCHEMA_VERSION, manifest))) {
            throw new BusinessException(ErrorCode.CONFLICT, "MANIFEST_MISMATCH");
        }
        return manifest;
    }

    void reconcile(ExperimentEntity experiment) {
        ExperimentStatus status = ExperimentStatus.valueOf(experiment.getStatus());
        if (status.terminal() || status == ExperimentStatus.CREATED || status == ExperimentStatus.PAUSED) {
            return;
        }
        List<ExperimentVariantEntity> variants = variantsOf(experiment.getId());
        if (variants.stream().anyMatch(variant -> variant.getEvaluationRunId() == null)) {
            return;
        }
        List<EvaluationRunEntity> runs = runMapper.selectBatchIds(
                variants.stream().map(ExperimentVariantEntity::getEvaluationRunId).toList());
        if (runs.size() != variants.size()) {
            persistenceService.updateStatus(experiment.getId(), ExperimentStatus.PAUSED,
                    "RECONCILIATION_BLOCKED", "Experiment Run 关联不完整");
            return;
        }
        boolean allTerminal = runs.stream().allMatch(run -> EvaluationRunStatus.valueOf(run.getStatus()).terminal());
        if (!allTerminal) {
            return;
        }
        if (status == ExperimentStatus.CANCEL_PENDING
                && runs.stream().allMatch(run -> EvaluationRunStatus.CANCELED.name().equals(run.getStatus()))) {
            persistenceService.updateStatus(experiment.getId(), ExperimentStatus.CANCELED,
                    "CANCEL_REQUESTED", null);
            return;
        }
        boolean clean = runs.stream().allMatch(run -> EvaluationRunStatus.COMPLETED.name().equals(run.getStatus()));
        persistenceService.updateStatus(experiment.getId(),
                clean ? ExperimentStatus.COMPLETED : ExperimentStatus.COMPLETED_WITH_ERRORS, null, null);
    }

    void reconcile(Long experimentId) {
        reconcile(requireExperiment(experimentId));
    }

    private ExperimentVO detailInternal(ExperimentEntity experiment) {
        return toVO(experiment, variantsOf(experiment.getId()));
    }

    private ExperimentVO toVO(ExperimentEntity experiment, List<ExperimentVariantEntity> variants) {
        return new ExperimentVO(experiment.getId(), experiment.getSpaceId(), experiment.getDatasetVersionId(),
                experiment.getStatus(), experiment.getManifestSchemaVersion(), experiment.getManifestHash(),
                experiment.getAuthorizedTokenBudget(), experiment.getFailureCode(), experiment.getFailureMessage(),
                experiment.getCreatedBy(), experiment.getStartedBy(), experiment.getStartedAt(),
                experiment.getCancelRequestedBy(), experiment.getCancelRequestedAt(), experiment.getFinishedAt(),
                variants.stream().map(this::toVariantVO).toList());
    }

    private ExperimentVariantVO toVariantVO(ExperimentVariantEntity variant) {
        List<String> diff = JsonUtils.parse(variant.getPromptDiffFieldPaths(),
                new TypeReference<List<String>>() { });
        return new ExperimentVariantVO(variant.getId(), variant.getExperimentId(), variant.getVariantKey(),
                variant.getRole(), variant.getVariantType(), variant.getCandidateConfigId(),
                variant.getSourceSnapshotSchemaVersion(), variant.getSourceSnapshotHash(),
                variant.getCandidateSnapshotSchemaVersion(), variant.getCandidateSnapshotHash(), diff,
                variant.getSnapshotWithoutPromptHash(), variant.getEvaluationRunId());
    }

    private ExperimentEntity requireExperiment(Long id) {
        ExperimentEntity experiment = experimentMapper.selectById(id);
        if (experiment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Experiment 不存在");
        }
        return experiment;
    }

    private ExperimentEntity findByRequestKey(Long spaceId, String requestKey) {
        return experimentMapper.selectOne(new LambdaQueryWrapper<ExperimentEntity>()
                .eq(ExperimentEntity::getSpaceId, spaceId)
                .eq(ExperimentEntity::getClientRequestKey, requestKey));
    }

    private List<ExperimentVariantEntity> variantsOf(Long experimentId) {
        return variantMapper.selectList(new LambdaQueryWrapper<ExperimentVariantEntity>()
                .eq(ExperimentVariantEntity::getExperimentId, experimentId)
                .orderByAsc(ExperimentVariantEntity::getId));
    }

    private void requireSameRequest(ExperimentEntity existing, String requestHash) {
        if (!Objects.equals(existing.getRequestHash(), requestHash)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Experiment 幂等键已被不同请求使用");
        }
    }

    private String derivationKey(Long experimentId, Long variantId, Long testCaseVersionId, Integer attemptNo) {
        return "experiment:" + experimentId + ":variant:" + variantId + ":case:"
                + testCaseVersionId + ":attempt:" + attemptNo;
    }

    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "内部服务调用失败" : result.message());
        }
        return result.data();
    }

    private record CreateRequest(Long spaceId, Long datasetVersionId,
                                 List<PromptCandidateCreateDTO> candidateVariants) { }
    private record CandidateDraft(String variantKey, AgentCandidateConfigVO identity) { }
    private record ManifestBuild(ExperimentManifest manifest, List<ReplaySourceVO> sources) { }
    private record ExperimentManifest(Long datasetVersionId, List<ManifestCase> cases,
                                      Integer sourceSnapshotSchemaVersion, String sourceSnapshotHash,
                                      Integer metricCatalogVersion, List<MetricManifest> metrics,
                                      String metricCatalogHash) { }
    private record ManifestCase(Long testCaseVersionId, Long sourceTaskId, Long sourceExecutionId,
                                Integer inputSnapshotSchemaVersion, String inputSnapshotHash,
                                Long documentId, Long documentVersionSnapshot,
                                String documentContentSha256, List<ManifestEvaluator> evaluators) { }
    private record ManifestEvaluator(Long evaluatorVersionId, String evaluatorKey, String contentHash) { }
    private record MetricManifest(String metricKey, String valueType, String unit, boolean currencyUnit,
                                  String direction, String source, String evaluatorKey) {
        private static MetricManifest from(MetricDefinition definition) {
            return new MetricManifest(definition.metricKey(), definition.valueType().name(), definition.unit(),
                    definition.currencyUnit(), definition.direction().name(), definition.source().name(),
                    definition.evaluatorKey());
        }
    }
}
