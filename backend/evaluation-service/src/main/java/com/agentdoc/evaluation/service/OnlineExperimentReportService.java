package com.agentdoc.evaluation.service;

import static com.agentdoc.common.constant.SpacePermissionConstant.*;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineTaskFeign;
import com.agentdoc.common.feign.OnlineAgentFeign;
import com.agentdoc.common.feign.dto.OnlineReportFactsQueryDTO;
import com.agentdoc.common.feign.vo.OnlineReportTaskFactVO;
import com.agentdoc.common.feign.vo.OnlineReportExecutionFactVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.enums.OnlineReviewExposureSource;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentReportMapper;
import com.agentdoc.evaluation.pojo.dto.OnlineReportRecalculateDTO;
import com.agentdoc.evaluation.pojo.dto.OnlineExperimentDecisionDTO;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentReportEntity;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentReportVO;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentReportRevisionVO;
import com.agentdoc.evaluation.pojo.vo.OnlineReportRangeSuggestionVO;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;
import java.util.stream.Collectors;

/** 显式报告入口：当前人类授权、批量求证、纯计算、短事务追加。 */
@Service
@RequiredArgsConstructor
public class OnlineExperimentReportService {
    private final OnlineExperimentMapper experiments;
    private final OnlineExperimentReportMapper reports;
    private final OnlineExecutionAuthority authority;
    private final OnlineReportSnapshotService snapshots;
    private final OnlineReportPersistenceService persistence;
    private final OnlineTaskFeign tasks;
    private final OnlineAgentFeign agents;
    private final SpaceAccessService access;
    private final OnlineReviewExposureService exposures;

    public OnlineReportRangeSuggestionVO rangeSuggestion(String id) {
        var experiment = require(id); read(experiment);
        access.requireOwner(experiment.getSpaceId()); access.requirePermission(experiment.getSpaceId(), EVALUATION_MANAGE);
        exposures.record(experiment.getId(), experiment.getSpaceId(), AuthUtils.getUserIdOrException(), OnlineReviewExposureSource.REPORT);
        var zone = ZoneId.systemDefault();
        return new OnlineReportRangeSuggestionVO(id, Long.toString(experiment.getAcceptedSequence()),
                LocalDateTime.now(zone).truncatedTo(ChronoUnit.MILLIS), zone.getId());
    }

    public PageVO<OnlineExperimentReportRevisionVO> revisions(String id, PageParam param) {
        param.validate();
        var experiment = require(id);
        read(experiment);
        exposures.record(experiment.getId(), experiment.getSpaceId(), AuthUtils.getUserIdOrException(), OnlineReviewExposureSource.REPORT);
        var page = reports.selectPage(new Page<OnlineExperimentReportEntity>(param.getPageNum(), param.getPageSize()),
                new LambdaQueryWrapper<OnlineExperimentReportEntity>()
                        .select(OnlineExperimentReportEntity::getExperimentId, OnlineExperimentReportEntity::getRevision,
                                OnlineExperimentReportEntity::getSchemaVersion, OnlineExperimentReportEntity::getReportHash,
                                OnlineExperimentReportEntity::getCreatedAt, OnlineExperimentReportEntity::getObservedAt)
                        .eq(OnlineExperimentReportEntity::getExperimentId, experiment.getId())
                        .eq(OnlineExperimentReportEntity::getSpaceId, experiment.getSpaceId())
                        .orderByDesc(OnlineExperimentReportEntity::getRevision));
        return PageVO.of(page.getRecords().stream().map(row -> new OnlineExperimentReportRevisionVO(
                id, row.getRevision(), row.getSchemaVersion(), row.getReportHash(), row.getCreatedAt(), row.getObservedAt(),
                experiment.getDecision() != null && Objects.equals(experiment.getDecisionReportRevision(), row.getRevision())
                        && Objects.equals(experiment.getDecisionReportHash(), row.getReportHash()))).toList(), page.getTotal(),
                param);
    }

    public OnlineExperimentReportVO detail(String id, Integer revision) {
        var experiment = require(id); read(experiment);
        exposures.record(experiment.getId(), experiment.getSpaceId(), AuthUtils.getUserIdOrException(), OnlineReviewExposureSource.REPORT);
        return OnlineReportPersistenceService.view(persistence.completeInput(reports.selectOne(new LambdaQueryWrapper<OnlineExperimentReportEntity>()
                .eq(OnlineExperimentReportEntity::getExperimentId, experiment.getId()).eq(OnlineExperimentReportEntity::getRevision, revision))));
    }

    public OnlineExperimentReportVO summary(String id, Integer revision) {
        var experiment = require(id); read(experiment);
        exposures.record(experiment.getId(), experiment.getSpaceId(), AuthUtils.getUserIdOrException(), OnlineReviewExposureSource.REPORT);
        var query = new LambdaQueryWrapper<OnlineExperimentReportEntity>().select(OnlineExperimentReportEntity.class,
                field -> !"input_json".equals(field.getColumn()))
                .eq(OnlineExperimentReportEntity::getExperimentId, experiment.getId()).eq(OnlineExperimentReportEntity::getRevision, revision);
        return OnlineReportPersistenceService.summary(reports.selectOne(query));
    }

    public OnlineExperimentReportVO recalculate(String id, OnlineReportRecalculateDTO request) {
        var experiment = require(id); access.requireOwner(experiment.getSpaceId()); access.requirePermission(experiment.getSpaceId(), EVALUATION_MANAGE);
        if (request == null) { throw invalid(); } key(request.clientRequestKey());
        long sequence; LocalDateTime cutoff;
        try {
            if (request.assignmentCutoffSequence() == null || !request.assignmentCutoffSequence().matches("0|[1-9][0-9]{0,18}")) { throw invalid(); }
            sequence = Long.parseLong(request.assignmentCutoffSequence()); cutoff = LocalDateTime.parse(request.observationCutoffAt());
            if (!cutoff.equals(cutoff.truncatedTo(ChronoUnit.MILLIS)) || cutoff.isAfter(LocalDateTime.now()) || sequence > experiment.getAcceptedSequence()
                    || cutoff.isBefore(experiment.getCreatedAt())) { throw invalid(); }
        } catch (RuntimeException malformed) { throw invalid(); }
        Long actor = AuthUtils.getUserIdOrException();
        exposures.record(experiment.getId(), experiment.getSpaceId(), actor, OnlineReviewExposureSource.REPORT);
        String requestHash = OnlineProtocolUtils.hash("online.report-request", Map.of("experimentId", id, "actorId", actor.toString(), "request", request));
        var prior = persistence.prior(experiment.getId(), actor, request.clientRequestKey(), requestHash);
        if (prior != null) { return OnlineReportPersistenceService.view(prior); }
        var manifest = authority.manifest(experiment); var snapshot = snapshots.load(experiment, sequence, cutoff);
        var taskFacts = new ArrayList<OnlineReportTaskFactVO>(); var executionFacts = new ArrayList<OnlineReportExecutionFactVO>(); var issues = new ArrayList<String>();
        for (int from = 0; from < snapshot.assignments().size(); from += 100) {
            var rows = snapshot.assignments().subList(from, Math.min(from + 100, snapshot.assignments().size()));
            var bindings = rows.stream().map(authority::binding).toList();
            var batch = new OnlineReportFactsQueryDTO(experiment.getSpaceId().toString(), id, bindings);
            try { taskFacts.addAll(data(tasks.reportFacts(batch))); }
            catch (RuntimeException unavailable) { issues.add(unavailable instanceof BusinessException business && business.getCode() == ErrorCode.FORBIDDEN.getCode() ? "ACCESS_DENIED_TASK" : "UNKNOWN_TASK_SOURCE"); }
            try { executionFacts.addAll(data(agents.reportFacts(batch))); }
            catch (RuntimeException unavailable) { issues.add(unavailable instanceof BusinessException business && business.getCode() == ErrorCode.FORBIDDEN.getCode() ? "ACCESS_DENIED_EXECUTION" : "UNKNOWN_EXECUTION_SOURCE"); }
        }
        validateFacts(snapshot, taskFacts, executionFacts);
        taskFacts.sort(Comparator.comparing(fact -> OnlineProtocolUtils.id(fact.taskId())));
        executionFacts.sort(Comparator.comparing(fact -> OnlineProtocolUtils.id(fact.taskId())));
        var calculation = OnlineReportCalculator.calculate(experiment, manifest, sequence, cutoff, snapshot, taskFacts, executionFacts, issues);
        var input = Map.of("calculationVersion", OnlineReportCalculator.IMPLEMENTATION, "manifestHash", experiment.getManifestHash(),
                "assignmentCutoffSequence", Long.toString(sequence), "observationCutoffAt", cutoff.toString(), "facts", snapshot.frozen(),
                "tasks", taskFacts, "executions", executionFacts, "missingSources", issues.stream().distinct().sorted().toList(), "actualWindows", calculation.path("actualWindows"));
        String inputJson = OnlineProtocolUtils.canonical("online.report-input", input);
        String reportJson = OnlineProtocolUtils.canonical("online.report", calculation);
        return OnlineReportPersistenceService.view(persistence.save(experiment.getId(), actor, request.clientRequestKey(), requestHash,
                experiment.getManifestHash(), sequence, cutoff, inputJson, reportJson));
    }

    public void decide(String id, OnlineExperimentDecisionDTO request) {
        var experiment = require(id); access.requireOwner(experiment.getSpaceId()); access.requirePermission(experiment.getSpaceId(), EVALUATION_MANAGE);
        if (request == null || request.reportRevision() == null || request.reportRevision() <= 0 || request.reportHash() == null || !request.reportHash().matches("[0-9a-f]{64}")
                || !Set.of("ACCEPTED", "REJECTED", "INSUFFICIENT_EVIDENCE").contains(request.decision()) || request.reason() == null || request.reason().isBlank()
                || request.reason().length() > 2000 || request.safetyFeedbackId() != null && !"REJECTED".equals(request.decision())) { throw invalid(); }
        key(request.clientRequestKey()); if (request.safetyFeedbackId() != null) { OnlineProtocolUtils.id(request.safetyFeedbackId()); }
        Long actor = AuthUtils.getUserIdOrException();
        persistence.decide(experiment.getId(), actor, request, OnlineProtocolUtils.hash("online.report-decision", Map.of("experimentId", id, "actorId", actor.toString(), "request", request)));
    }

    private static void validateFacts(OnlineReportSnapshotService.Snapshot snapshot, List<OnlineReportTaskFactVO> tasks, List<OnlineReportExecutionFactVO> executions) {
        var rows = snapshot.assignments().stream().collect(Collectors.toMap(row -> row.getTaskId().toString(), row -> row));
        var seen = new HashSet<String>();
        for (var fact : tasks) {
            var row = rows.get(fact.taskId());
            if (row == null || !seen.add(fact.taskId()) || !row.getId().toString().equals(fact.assignmentId()) || !Objects.equals(row.getBindingHash(), fact.bindingHash())
                    || row.getExecutionId() != null && fact.executionId() != null && !row.getExecutionId().toString().equals(fact.executionId())) { throw invalid(); }
        }
        seen.clear();
        for (var fact : executions) {
            var row = rows.get(fact.taskId());
            if (row == null || !seen.add(fact.taskId()) || !row.getId().toString().equals(fact.assignmentId()) || !Objects.equals(row.getBindingHash(), fact.bindingHash())
                    || row.getExecutionId() != null && fact.executionId() != null && !row.getExecutionId().toString().equals(fact.executionId())) { throw invalid(); }
        }
    }
    private OnlineExperimentEntity require(String id) {
        AuthUtils.getUserIdOrException(); var row = experiments.selectById(OnlineProtocolUtils.id(id));
        if (row == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "线上实验不存在"); } return row;
    }
    private void read(OnlineExperimentEntity row) { access.requirePermission(row.getSpaceId(), SPACE_READ); access.requirePermission(row.getSpaceId(), EVALUATION_READ); }
    private static void key(String key) { if (key == null || !key.matches("[A-Za-z0-9._:-]{1,64}")) { throw invalid(); } }
    private static <T> T data(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) { throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(), "报告来源不可读取"); } return result.data();
    }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.BAD_REQUEST, "报告请求或来源身份无效"); }
}
