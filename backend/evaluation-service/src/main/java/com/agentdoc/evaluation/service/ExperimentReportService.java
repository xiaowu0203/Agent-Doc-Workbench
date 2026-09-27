package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.vo.AgentCandidateConfigVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.enums.ExperimentStatus;
import com.agentdoc.evaluation.mapper.ExperimentMapper;
import com.agentdoc.evaluation.mapper.ExperimentReportMapper;
import com.agentdoc.evaluation.mapper.ExperimentVariantMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseVersionMapper;
import com.agentdoc.evaluation.pojo.dto.ExperimentDecisionDTO;
import com.agentdoc.evaluation.pojo.dto.ExperimentReportRecalculateDTO;
import com.agentdoc.evaluation.pojo.entity.ExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentReportEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentVariantEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseVersionEntity;
import com.agentdoc.evaluation.pojo.vo.ExperimentReportRevisionVO;
import com.agentdoc.evaluation.pojo.vo.ExperimentReportVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_READ;

/** Experiment 不可变报告、显式重算和人工结论入口。 */
@Service
@RequiredArgsConstructor
public class ExperimentReportService {

    private static final int CALCULATION_SCHEMA_VERSION = 1;
    private static final int REPORT_SCHEMA_VERSION = 1;

    private final ExperimentMapper experimentMapper;
    private final ExperimentVariantMapper variantMapper;
    private final EvaluationTestCaseVersionMapper testCaseVersionMapper;
    private final ExperimentReportMapper reportMapper;
    private final ExperimentReportSnapshotService snapshotService;
    private final ExperimentReportEvidenceService evidenceService;
    private final ExperimentReportPersistenceService persistenceService;
    private final SpaceAccessService spaceAccessService;
    private final AgentFeign agentFeign;

    public List<ExperimentReportRevisionVO> list(Long experimentId) {
        ExperimentEntity experiment = requireExperiment(experimentId);
        spaceAccessService.requirePermission(experiment.getSpaceId(), EVALUATION_READ);
        return reportMapper.selectList(new LambdaQueryWrapper<ExperimentReportEntity>()
                .eq(ExperimentReportEntity::getExperimentId, experimentId)
                .orderByAsc(ExperimentReportEntity::getRevision)).stream().map(this::toRevisionVO).toList();
    }

    public ExperimentReportVO detail(Long experimentId, Integer revision) {
        ExperimentEntity experiment = requireExperiment(experimentId);
        spaceAccessService.requirePermission(experiment.getSpaceId(), EVALUATION_READ);
        ExperimentReportEntity report = reportMapper.selectOne(new LambdaQueryWrapper<ExperimentReportEntity>()
                .eq(ExperimentReportEntity::getExperimentId, experimentId)
                .eq(ExperimentReportEntity::getRevision, revision));
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报告 revision 不存在");
        }
        return toVO(report);
    }

    public ExperimentReportVO recalculate(Long experimentId, ExperimentReportRecalculateDTO request) {
        ExperimentEntity experiment = requireExperiment(experimentId);
        spaceAccessService.requirePermission(experiment.getSpaceId(), EVALUATION_MANAGE);
        if (request == null || request.clientRequestKey() == null || request.clientRequestKey().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报告重算幂等键不能为空");
        }
        String requestHash = StableSnapshotUtils.snapshotHash(1,
                new RecalculationRequest(experimentId, request.clientRequestKey()));
        ExperimentReportEntity prior = persistenceService.existingRequest(
                experimentId, request.clientRequestKey(), requestHash);
        if (prior != null) {
            return toVO(prior);
        }
        ensureAutomatic(experimentId);
        PreparedReport prepared = prepare(experiment);
        return toVO(persistenceService.save(experimentId, prepared.persistence(),
                request.clientRequestKey(), requestHash, AuthUtils.getUserIdOrException()));
    }

    /** 终态首次自动报告；重复扫描只返回已有 revision，不生成新版本。 */
    public void ensureAutomatic(Long experimentId) {
        ExperimentEntity experiment = requireExperiment(experimentId);
        ExperimentStatus status = ExperimentStatus.valueOf(experiment.getStatus());
        if (status != ExperimentStatus.COMPLETED && status != ExperimentStatus.COMPLETED_WITH_ERRORS) {
            return;
        }
        ExperimentReportEntity existing = reportMapper.selectOne(new LambdaQueryWrapper<ExperimentReportEntity>()
                .eq(ExperimentReportEntity::getExperimentId, experimentId)
                .orderByAsc(ExperimentReportEntity::getRevision).last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        PreparedReport prepared = prepare(experiment);
        persistenceService.save(experimentId, prepared.persistence(), null, null, experiment.getStartedBy());
    }

    public void decide(Long experimentId, ExperimentDecisionDTO request) {
        ExperimentEntity experiment = requireExperiment(experimentId);
        spaceAccessService.requirePermission(experiment.getSpaceId(), EVALUATION_MANAGE);
        if (request == null || request.reportRevision() == null || request.reportRevision() <= 0
                || request.decision() == null || request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "人工结论参数无效");
        }
        persistenceService.decide(experimentId, request.reportRevision(), request.decision().name(),
                request.reason().trim(), AuthUtils.getUserIdOrException());
    }

    private PreparedReport prepare(ExperimentEntity experiment) {
        if (experiment.getManifestSchemaVersion() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "MANIFEST_MISMATCH");
        }
        ExperimentManifest manifest = JsonUtils.parse(experiment.getManifestJson(), ExperimentManifest.class);
        if (manifest == null || !Objects.equals(experiment.getManifestHash(),
                StableSnapshotUtils.snapshotHash(experiment.getManifestSchemaVersion(), manifest))) {
            throw new BusinessException(ErrorCode.CONFLICT, "MANIFEST_MISMATCH");
        }
        verifySourceCaseIdentity(experiment, manifest);
        List<ExperimentVariantEntity> variants = variantMapper.selectList(
                new LambdaQueryWrapper<ExperimentVariantEntity>()
                        .eq(ExperimentVariantEntity::getExperimentId, experiment.getId())
                        .orderByAsc(ExperimentVariantEntity::getId));
        verifyCandidateProofs(experiment, manifest, variants);
        ExperimentReportSnapshotService.Snapshot snapshot = snapshotService.load(experiment, manifest, variants);
        List<ExperimentReportMatrix.Cell> cells = ExperimentReportMatrix.align(manifest, snapshot.variants());
        List<Long> selectedMetricIds = cells.stream().map(ExperimentReportMatrix.Cell::metricId)
                .filter(Objects::nonNull).distinct().sorted().toList();
        ExperimentReportEvidenceService.Evidence evidence = evidenceService.load(experiment.getSpaceId(),
                snapshot, selectedMetricIds);
        List<ReportedCell> reportedCells = cells.stream().map(cell -> new ReportedCell(cell,
                evidence.metricEvidence().stream().filter(item -> Objects.equals(item.metricId(), cell.metricId()))
                        .map(ExperimentReportEvidenceService.MetricEvidence::evidenceId).toList())).toList();
        List<ExperimentReportAggregate.VariantSummary> summaries =
                ExperimentReportAggregate.summarize(manifest, cells);
        List<ExperimentReportAggregate.PairSummary> comparisons =
                ExperimentReportAggregate.compare(manifest, cells);
        Long actualTokens = actualTokens(cells, manifest.cases().size() * variants.size());
        Long overrun = actualTokens == null || experiment.getAuthorizedTokenBudget() == null ? null
                : Math.max(0L, actualTokens - experiment.getAuthorizedTokenBudget());
        List<FeedbackCoverage> coverage = feedbackCoverage(variants, evidence.feedback());
        SelectedIds selected = new SelectedIds(snapshot.runIds(), snapshot.attemptIds(), snapshot.resultIds(),
                selectedMetricIds, evidence.evidenceIds(), evidence.feedbackIds());
        ReportContent content = new ReportContent(manifest.cases().size(), variants.size(), snapshot.cases(),
                reportedCells, summaries, comparisons, evidence.metricEvidence(), evidence.feedback(), coverage,
                experiment.getAuthorizedTokenBudget(), actualTokens, overrun);
        String selectedJson = JsonUtils.toJson(selected);
        String reportJson = JsonUtils.toJson(content);
        String calculationInputHash = StableSnapshotUtils.snapshotHash(CALCULATION_SCHEMA_VERSION,
                new CalculationInput(experiment.getManifestHash(), selected,
                        experiment.getAuthorizedTokenBudget()));
        return new PreparedReport(new ExperimentReportPersistenceService.Prepared(experiment.getManifestHash(),
                CALCULATION_SCHEMA_VERSION, calculationInputHash, selectedJson, REPORT_SCHEMA_VERSION,
                reportJson, StableSnapshotUtils.sha256Utf8(reportJson)));
    }

    private void verifyCandidateProofs(ExperimentEntity experiment, ExperimentManifest manifest,
                                       List<ExperimentVariantEntity> variants) {
        if (variants.size() < 2 || variants.stream().filter(item -> "BASELINE".equals(item.getRole())).count() != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "REPORT_INPUT_INCOMPLETE");
        }
        String commonHash = variants.stream().filter(item -> "BASELINE".equals(item.getRole()))
                .findFirst().orElseThrow().getSnapshotWithoutPromptHash();
        for (ExperimentVariantEntity variant : variants) {
            if (!Objects.equals(variant.getSourceSnapshotHash(), manifest.sourceSnapshotHash())
                    || !Objects.equals(variant.getSnapshotWithoutPromptHash(), commonHash)
                    || !Objects.equals(variant.getSourceSnapshotSchemaVersion(), manifest.sourceSnapshotSchemaVersion())
                    || !"PROMPT".equals(variant.getVariantType())) {
                throw new BusinessException(ErrorCode.CONFLICT, "CANDIDATE_CONFIG_TAMPERED");
            }
            if ("BASELINE".equals(variant.getRole())) {
                if (variant.getCandidateConfigId() != null
                        || !"baseline".equals(variant.getVariantKey())
                        || !Objects.equals(variant.getCandidateSnapshotHash(), manifest.sourceSnapshotHash())) {
                    throw new BusinessException(ErrorCode.CONFLICT, "CANDIDATE_CONFIG_TAMPERED");
                }
                continue;
            }
            if (!"CANDIDATE".equals(variant.getRole()) || variant.getCandidateConfigId() == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "CANDIDATE_CONFIG_TAMPERED");
            }
            Result<AgentCandidateConfigVO> response = agentFeign.getCandidateConfigIdentity(
                    variant.getCandidateConfigId(), experiment.getSpaceId(), variant.getCandidateSnapshotHash());
            AgentCandidateConfigVO identity = response == null || response.code() != ErrorCode.SUCCESS.getCode()
                    ? null : response.data();
            List<String> diffPaths = JsonUtils.parse(variant.getPromptDiffFieldPaths(),
                    new TypeReference<List<String>>() { });
            if (identity == null || diffPaths == null
                    || diffPaths.stream().anyMatch(path -> !"snapshot.systemPrompt".equals(path))
                    || !Objects.equals(identity.candidateConfigId(), variant.getCandidateConfigId())
                    || !Objects.equals(identity.spaceId(), experiment.getSpaceId())
                    || !Objects.equals(identity.sourceTaskId(), manifest.cases().getFirst().sourceTaskId())
                    || !Objects.equals(identity.sourceExecutionId(), manifest.cases().getFirst().sourceExecutionId())
                    || !Objects.equals(identity.sourceSnapshotSchemaVersion(),
                    variant.getSourceSnapshotSchemaVersion())
                    || !Objects.equals(identity.candidateSnapshotSchemaVersion(),
                    variant.getCandidateSnapshotSchemaVersion())
                    || !Objects.equals(identity.sourceSnapshotHash(), variant.getSourceSnapshotHash())
                    || !Objects.equals(identity.candidateSnapshotHash(), variant.getCandidateSnapshotHash())
                    || !Objects.equals(identity.snapshotWithoutPromptHash(), commonHash)
                    || !Objects.equals(identity.promptDiffFieldPaths(), diffPaths)) {
                throw new BusinessException(ErrorCode.CONFLICT, "CANDIDATE_CONFIG_TAMPERED");
            }
        }
    }

    private void verifySourceCaseIdentity(ExperimentEntity experiment, ExperimentManifest manifest) {
        if (!Objects.equals(manifest.datasetVersionId(), experiment.getDatasetVersionId())
                || manifest.cases().isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "MANIFEST_MISMATCH");
        }
        Map<Long, EvaluationTestCaseVersionEntity> versions = testCaseVersionMapper.selectBatchIds(
                manifest.cases().stream().map(ManifestCase::testCaseVersionId).toList()).stream()
                .collect(Collectors.toMap(EvaluationTestCaseVersionEntity::getId, Function.identity()));
        for (ManifestCase expected : manifest.cases()) {
            EvaluationTestCaseVersionEntity version = versions.get(expected.testCaseVersionId());
            if (version == null || !Objects.equals(version.getSpaceId(), experiment.getSpaceId())
                    || !Objects.equals(version.getSourceTaskId(), expected.sourceTaskId())
                    || !Objects.equals(version.getSourceExecutionId(), expected.sourceExecutionId())
                    || !Objects.equals(version.getSourceInputSchemaVersion(), expected.inputSnapshotSchemaVersion())
                    || !Objects.equals(version.getSourceInputHash(), expected.inputSnapshotHash())
                    || !Objects.equals(version.getSourceExecutionSchemaVersion(),
                    manifest.sourceSnapshotSchemaVersion())
                    || !Objects.equals(version.getSourceExecutionHash(), manifest.sourceSnapshotHash())
                    || !Objects.equals(version.getDocumentVersionSnapshot(), expected.documentVersionSnapshot())
                    || !Objects.equals(version.getDocumentContentSha256(), expected.documentContentSha256())) {
                throw new BusinessException(ErrorCode.CONFLICT, "REPORT_INPUT_INCOMPLETE");
            }
        }
    }

    private Long actualTokens(List<ExperimentReportMatrix.Cell> cells, int expectedTasks) {
        List<ExperimentReportMatrix.Cell> values = cells.stream()
                .filter(cell -> "execution.total-tokens".equals(cell.metricKey())).toList();
        if (values.size() != expectedTasks || values.stream().anyMatch(cell -> cell.missingReason() != null)) {
            return null;
        }
        return values.stream().mapToLong(cell -> cell.numericValue().longValueExact()).sum();
    }

    private List<FeedbackCoverage> feedbackCoverage(List<ExperimentVariantEntity> variants,
            List<ExperimentReportEvidenceService.CaseFeedback> feedback) {
        List<FeedbackCoverage> coverage = new ArrayList<>();
        for (ExperimentVariantEntity variant : variants) {
            for (String source : List.of("MANUAL", "CHANGE_REQUEST")) {
                long count = feedback.stream().filter(item -> Objects.equals(item.variantKey(), variant.getVariantKey())
                        && source.equals(item.sourceType()))
                        .map(ExperimentReportEvidenceService.CaseFeedback::testCaseVersionId)
                        .distinct().count();
                coverage.add(new FeedbackCoverage(variant.getVariantKey(), source, count));
            }
        }
        return List.copyOf(coverage);
    }

    private ExperimentEntity requireExperiment(Long id) {
        ExperimentEntity experiment = experimentMapper.selectById(id);
        if (experiment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Experiment 不存在");
        }
        return experiment;
    }

    private ExperimentReportRevisionVO toRevisionVO(ExperimentReportEntity report) {
        return new ExperimentReportRevisionVO(report.getExperimentId(), report.getRevision(),
                report.getReportSchemaVersion(), report.getContentHash(), report.getGeneratedBy(),
                report.getCreatedAt());
    }

    private ExperimentReportVO toVO(ExperimentReportEntity report) {
        JsonNode selected = JsonUtils.parse(report.getSelectedRecordIdsJson(), JsonNode.class);
        JsonNode content = JsonUtils.parse(report.getReportJson(), JsonNode.class);
        return new ExperimentReportVO(report.getExperimentId(), report.getRevision(),
                report.getReportSchemaVersion(), report.getManifestHash(), report.getCalculationInputHash(),
                selected, content, report.getContentHash(), report.getGeneratedBy(), report.getCreatedAt());
    }

    private record RecalculationRequest(Long experimentId, String clientRequestKey) { }
    private record CalculationInput(String manifestHash, SelectedIds selected, Long authorizedTokenBudget) { }
    private record PreparedReport(ExperimentReportPersistenceService.Prepared persistence) { }
    private record SelectedIds(List<Long> runIds, List<Long> attemptIds, List<Long> resultIds,
                               List<Long> metricIds, List<Long> evidenceIds, List<Long> feedbackIds) { }
    private record ReportedCell(ExperimentReportMatrix.Cell value, List<Long> evidenceIds) { }
    private record FeedbackCoverage(String variantKey, String sourceType, long coveredCaseCount) { }
    private record ReportContent(int expectedCaseCount, int variantCount,
                                 List<ExperimentReportSnapshotService.CaseSelection> cases,
                                 List<ReportedCell> cells,
                                 List<ExperimentReportAggregate.VariantSummary> summaries,
                                 List<ExperimentReportAggregate.PairSummary> comparisons,
                                 List<ExperimentReportEvidenceService.MetricEvidence> metricEvidence,
                                 List<ExperimentReportEvidenceService.CaseFeedback> feedback,
                                 List<FeedbackCoverage> feedbackCoverage,
                                 Long authorizedTokenBudget, Long actualTokenUsage, Long budgetOverrun) { }
}
