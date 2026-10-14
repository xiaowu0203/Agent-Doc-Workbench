package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentReportMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentActionRequestMapper;
import com.agentdoc.evaluation.mapper.OnlineReportInputChunkMapper;
import static com.agentdoc.evaluation.constant.OnlineReportConstant.*;
import com.agentdoc.evaluation.pojo.dto.OnlineExperimentDecisionDTO;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentReportEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentActionRequestEntity;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentReportVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Map;
import java.util.ArrayList;

/** 只在计算完成后持实验短锁，追加版本与请求映射原子提交。 */
@Service
@RequiredArgsConstructor
public class OnlineReportPersistenceService {
    private final OnlineExperimentMapper experiments;
    private final OnlineExperimentReportMapper reports;
    private final OnlineExperimentActionRequestMapper requests;
    private final OnlineReportInputChunkMapper chunks;

    public OnlineExperimentReportEntity prior(Long experiment, Long actor, String key, String requestHash) {
        var request = request(experiment, actor, "REPORT_RECALCULATE", key);
        if (request == null) { return null; }
        same(request.getRequestHash(), requestHash);
        var row = reports.selectById(OnlineProtocolUtils.id(JsonUtils.parseStrict(request.getResultJson(), JsonNode.class).path("reportId").asText()));
        if (row == null || !Objects.equals(row.getExperimentId(), experiment)) { throw conflict("REPORT_HASH_INVALID"); }
        return completeInput(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public OnlineExperimentReportEntity save(Long experimentId, Long actor, String key, String requestHash,
            String manifestHash, long sequence, LocalDateTime cutoff, String inputJson, String reportJson) {
        var experiment = experiments.lock(experimentId);
        if (experiment == null || !Objects.equals(experiment.getManifestHash(), manifestHash) || experiment.getAcceptedSequence() < sequence) { throw conflict("MANIFEST_INVALID"); }
        var prior = prior(experimentId, actor, key, requestHash); if (prior != null) { return prior; }
        String inputHash = StableSnapshotUtils.sha256Utf8(inputJson);
        var report = reports.selectOne(new LambdaQueryWrapper<OnlineExperimentReportEntity>().eq(OnlineExperimentReportEntity::getExperimentId, experimentId)
                .eq(OnlineExperimentReportEntity::getInputHash, inputHash));
        if (report == null) {
            var latest = reports.selectOne(new LambdaQueryWrapper<OnlineExperimentReportEntity>().eq(OnlineExperimentReportEntity::getExperimentId, experimentId)
                    .orderByDesc(OnlineExperimentReportEntity::getRevision).last("LIMIT 1"));
            report = new OnlineExperimentReportEntity(); report.setId(IdWorker.getId()); report.setExperimentId(experimentId); report.setSpaceId(experiment.getSpaceId());
            report.setRevision(latest == null ? 1 : Math.incrementExact(latest.getRevision())); report.setReportType(TYPE); report.setSchemaVersion(SCHEMA_VERSION);
            report.setInputHash(inputHash); report.setInputJson(inputJson); report.setReportJson(reportJson); report.setReportHash(StableSnapshotUtils.sha256Utf8(reportJson));
            var parts = new ArrayList<String>();
            if (inputJson.length() > INPUT_CHUNK_CHARACTERS) {
                for (int start = 0; start < inputJson.length();) {
                    int end = Math.min(start + INPUT_CHUNK_CHARACTERS, inputJson.length());
                    if (end < inputJson.length() && Character.isHighSurrogate(inputJson.charAt(end - 1))) { end--; }
                    parts.add(inputJson.substring(start, end)); start = end;
                    if (parts.size() > MAX_INPUT_CHUNKS) { throw conflict("REPORT_INPUT_TOO_LARGE"); }
                }
                report.setInputJson(OnlineProtocolUtils.canonical("online.report-input-chunks", Map.of("inputHash", inputHash, "chunkCount", parts.size())));
            }
            report.setObservedAt(cutoff); report.setCreatedBy(actor); reports.insert(report);
            for (int number = 0; number < parts.size(); number++) { String part = parts.get(number); chunks.insert(report.getId(), number, part, StableSnapshotUtils.sha256Utf8(part)); }
            report.setResolvedInputJson(inputJson);
        } else { same(report.getReportHash(), StableSnapshotUtils.sha256Utf8(reportJson)); }
        action(experimentId, actor, "REPORT_RECALCULATE", key, requestHash, JsonUtils.toJson(Map.of("reportId", report.getId().toString())), null);
        return completeInput(report);
    }

    @Transactional(rollbackFor = Exception.class)
    public void decide(Long experimentId, Long actor, OnlineExperimentDecisionDTO request, String requestHash) {
        var experiment = experiments.lock(experimentId); if (experiment == null) { throw conflict("ONLINE_EXPERIMENT_NOT_FOUND"); }
        var previous = request(experimentId, actor, "REPORT_DECISION", request.clientRequestKey());
        if (previous != null) { same(previous.getRequestHash(), requestHash); return; }
        var report = reports.selectOne(new LambdaQueryWrapper<OnlineExperimentReportEntity>().eq(OnlineExperimentReportEntity::getExperimentId, experimentId)
                .eq(OnlineExperimentReportEntity::getRevision, request.reportRevision()));
        var view = view(completeInput(report)); if (!"SUPPORTED".equals(view.state())) { throw conflict("REPORT_HASH_INVALID"); }
        same(report.getReportHash(), request.reportHash());
        same(view.report().path("payload").path("manifestHash").asText(), experiment.getManifestHash());
        var eligibility = view.report().path("payload").path("decisionEligibility");
        boolean safety = "REJECTED".equals(request.decision()) && request.safetyFeedbackId() != null;
        if (safety) {
            boolean found = false; for (var id : eligibility.path("confirmedSafetyFeedbackIds")) { found |= request.safetyFeedbackId().equals(id.asText()); }
            if (!found) { throw conflict("SAFETY_EVIDENCE_REQUIRED"); }
        } else if (!"STOPPED".equals(experiment.getStatus())) { throw conflict("OBSERVATION_NOT_FINISHED"); }
        if (!safety && !"INSUFFICIENT_EVIDENCE".equals(request.decision()) && !eligibility.path("comparisonAllowed").asBoolean()) {
            throw conflict("INSUFFICIENT_EVIDENCE");
        }
        if (experiment.getDecision() != null) {
            if (!Objects.equals(experiment.getDecision(), request.decision()) || !Objects.equals(experiment.getDecisionReportRevision(), request.reportRevision())
                    || !Objects.equals(experiment.getDecisionReportHash(), request.reportHash()) || !Objects.equals(experiment.getDecisionReason(), request.reason())
                    || !Objects.equals(experiment.getDecidedBy(), actor)) { throw conflict("DECISION_ALREADY_EXISTS"); }
        } else {
            experiment.setDecision(request.decision()); experiment.setDecisionReason(request.reason()); experiment.setDecisionReportRevision(request.reportRevision());
            experiment.setDecisionReportHash(request.reportHash()); experiment.setDecidedBy(actor); experiment.setDecidedAt(LocalDateTime.now()); experiments.updateById(experiment);
        }
        action(experimentId, actor, "REPORT_DECISION", request.clientRequestKey(), requestHash, JsonUtils.toJson(request), JsonUtils.toJson(request));
    }

    public static OnlineExperimentReportVO view(OnlineExperimentReportEntity row) {
        return view(row, false);
    }

    /** 概要读取不加载原始事实快照；正式决定仍须验证完整输入和报告。 */
    public static OnlineExperimentReportVO summary(OnlineExperimentReportEntity row) {
        return view(row, true);
    }

    private static OnlineExperimentReportVO view(OnlineExperimentReportEntity row, boolean summary) {
        if (row == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "报告版本不存在"); }
        String state = summary ? "SUMMARY_VERIFIED" : "SUPPORTED"; JsonNode body = null;
        if (!Integer.valueOf(SCHEMA_VERSION).equals(row.getSchemaVersion()) || !TYPE.equals(row.getReportType())) { state = "UNSUPPORTED_SCHEMA"; }
        else {
            try {
                body = OnlineProtocolUtils.object(row.getReportJson());
                String logicalInput = row.getResolvedInputJson() == null ? row.getInputJson() : row.getResolvedInputJson();
                var input = summary ? null : OnlineProtocolUtils.object(logicalInput);
                if (!Objects.equals(row.getReportHash(), StableSnapshotUtils.sha256Utf8(row.getReportJson()))
                        || !summary && !Objects.equals(row.getInputHash(), StableSnapshotUtils.sha256Utf8(logicalInput))
                        || !OnlineProtocolUtils.canonicalNode(body).equals(row.getReportJson()) || !"online.report".equals(body.path("domain").asText())
                        || body.path("schemaVersion").asInt() != 2 || body.path("payload").path("schemaVersion").asInt() != SCHEMA_VERSION
                        || !"ONLINE_V1".equals(body.path("payload").path("reportType").asText())
                        || !summary && (!"online.report-input".equals(input.path("domain").asText()) || input.path("schemaVersion").asInt() != 2
                                || !OnlineProtocolUtils.canonicalNode(input).equals(logicalInput))) { state = "HASH_MISMATCH"; body = null; }
            } catch (RuntimeException invalid) { state = "HASH_MISMATCH"; body = null; }
        }
        return new OnlineExperimentReportVO(row.getId().toString(), row.getExperimentId().toString(), row.getRevision(), row.getReportType(), row.getSchemaVersion(),
                row.getReportHash(), row.getInputHash(), state, body);
    }
    /** 不重读业务事实补缺块；只组装该不可变报告的完整输入。 */
    public OnlineExperimentReportEntity completeInput(OnlineExperimentReportEntity row) {
        if (row == null || row.getResolvedInputJson() != null) { return row; }
        try {
            var storage = OnlineProtocolUtils.object(row.getInputJson());
            if (!"online.report-input-chunks".equals(storage.path("domain").asText())) { return row; }
            int count = storage.path("payload").path("chunkCount").asInt();
            if (storage.path("schemaVersion").asInt() != 2 || count < 1 || count > MAX_INPUT_CHUNKS || !row.getInputJson().equals(OnlineProtocolUtils.canonicalNode(storage))
                    || !row.getInputHash().equals(storage.path("payload").path("inputHash").asText())) { throw conflict("REPORT_INPUT_INVALID"); }
            var parts = chunks.find(row.getId()); if (parts.size() != count) { throw conflict("REPORT_INPUT_INVALID"); }
            var input = new StringBuilder();
            for (int index = 0; index < count; index++) {
                var part = parts.get(index);
                if (part.number() != index || part.content() == null || part.content().length() > INPUT_CHUNK_CHARACTERS || !Objects.equals(part.hash(), StableSnapshotUtils.sha256Utf8(part.content()))) { throw conflict("REPORT_INPUT_INVALID"); }
                input.append(part.content());
            }
            if (!row.getInputHash().equals(StableSnapshotUtils.sha256Utf8(input.toString()))) { throw conflict("REPORT_INPUT_INVALID"); }
            row.setResolvedInputJson(input.toString());
        } catch (IllegalArgumentException | BusinessException invalid) { row.setResolvedInputJson(null); }
        return row;
    }
    private OnlineExperimentActionRequestEntity request(Long experiment, Long actor, String action, String key) {
        return requests.selectOne(new LambdaQueryWrapper<OnlineExperimentActionRequestEntity>().eq(OnlineExperimentActionRequestEntity::getExperimentId, experiment)
                .eq(OnlineExperimentActionRequestEntity::getActorId, actor).eq(OnlineExperimentActionRequestEntity::getAction, action).eq(OnlineExperimentActionRequestEntity::getRequestKey, key));
    }
    private void action(Long experiment, Long actor, String action, String key, String hash, String result, String request) {
        var row = new OnlineExperimentActionRequestEntity(); row.setId(IdWorker.getId()); row.setExperimentId(experiment); row.setActorId(actor);
        row.setAction(action); row.setRequestKey(key); row.setRequestHash(hash); row.setResultJson(result); row.setRequestJson(request); requests.insert(row);
    }
    private static void same(Object left, Object right) { if (!Objects.equals(left, right)) { throw conflict("IDEMPOTENCY_CONFLICT"); } }
    private static BusinessException conflict(String reason) { return new BusinessException(ErrorCode.CONFLICT, reason); }
}
