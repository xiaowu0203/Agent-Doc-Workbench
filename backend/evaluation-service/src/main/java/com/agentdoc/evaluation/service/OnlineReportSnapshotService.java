package com.agentdoc.evaluation.service;

import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.agentdoc.evaluation.mapper.*;
import com.agentdoc.evaluation.pojo.entity.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.lang.reflect.Modifier;

/** 一次只读事务捕获本领域事实；远程观察在事务返回后完成。 */
@Service
@RequiredArgsConstructor
public class OnlineReportSnapshotService {
    private final OnlineAssignmentMapper assignments;
    private final OnlineEvaluationAttemptMapper attempts;
    private final EvaluationResultMapper results;
    private final EvaluationMetricMapper metrics;
    private final EvaluationEvidenceReferenceMapper evidence;
    private final EvaluationMetricEvidenceMapper links;
    private final EvaluationFeedbackMapper feedback;
    private final OnlineExperimentEventMapper events;

    @Transactional(readOnly = true)
    public Snapshot load(OnlineExperimentEntity experiment, long sequence, LocalDateTime cutoff) {
        var rows = assignments.selectList(new LambdaQueryWrapper<OnlineAssignmentEntity>()
                .eq(OnlineAssignmentEntity::getExperimentId, experiment.getId()).eq(OnlineAssignmentEntity::getSpaceId, experiment.getSpaceId())
                .le(OnlineAssignmentEntity::getAcceptedSequence, sequence).orderByAsc(OnlineAssignmentEntity::getAcceptedSequence));
        var ids = rows.stream().map(OnlineAssignmentEntity::getId).toList();
        if (ids.isEmpty()) { return new Snapshot(rows, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), eventRows(experiment, cutoff)); }
        var chosenAttempts = attempts.selectList(new LambdaQueryWrapper<OnlineEvaluationAttemptEntity>()
                .eq(OnlineEvaluationAttemptEntity::getSpaceId, experiment.getSpaceId()).in(OnlineEvaluationAttemptEntity::getAssignmentId, ids)
                .le(OnlineEvaluationAttemptEntity::getFinishedAt, cutoff).orderByAsc(OnlineEvaluationAttemptEntity::getId));
        var attemptIds = chosenAttempts.stream().map(OnlineEvaluationAttemptEntity::getId).toList();
        var chosenResults = attemptIds.isEmpty() ? List.<EvaluationResultEntity>of() : results.selectList(new LambdaQueryWrapper<EvaluationResultEntity>()
                .eq(EvaluationResultEntity::getSubjectType, "ONLINE_TASK").eq(EvaluationResultEntity::getSpaceId, experiment.getSpaceId())
                .in(EvaluationResultEntity::getOnlineEvaluationAttemptId, attemptIds).orderByAsc(EvaluationResultEntity::getId));
        var chosenMetrics = attemptIds.isEmpty() ? List.<EvaluationMetricEntity>of() : metrics.selectList(new LambdaQueryWrapper<EvaluationMetricEntity>()
                .eq(EvaluationMetricEntity::getSubjectType, "ONLINE_TASK").eq(EvaluationMetricEntity::getSpaceId, experiment.getSpaceId())
                .in(EvaluationMetricEntity::getOnlineEvaluationAttemptId, attemptIds).orderByAsc(EvaluationMetricEntity::getId));
        var chosenEvidence = attemptIds.isEmpty() ? List.<EvaluationEvidenceReferenceEntity>of() : evidence.selectList(new LambdaQueryWrapper<EvaluationEvidenceReferenceEntity>()
                .eq(EvaluationEvidenceReferenceEntity::getSubjectType, "ONLINE_TASK").eq(EvaluationEvidenceReferenceEntity::getSpaceId, experiment.getSpaceId())
                .in(EvaluationEvidenceReferenceEntity::getOnlineEvaluationAttemptId, attemptIds).orderByAsc(EvaluationEvidenceReferenceEntity::getId));
        var metricIds = chosenMetrics.stream().map(EvaluationMetricEntity::getId).toList();
        var chosenLinks = metricIds.isEmpty() ? List.<EvaluationMetricEvidenceEntity>of() : links.selectList(new LambdaQueryWrapper<EvaluationMetricEvidenceEntity>()
                .in(EvaluationMetricEvidenceEntity::getMetricId, metricIds).orderByAsc(EvaluationMetricEvidenceEntity::getMetricId)
                .orderByAsc(EvaluationMetricEvidenceEntity::getEvidenceReferenceId));
        var chosenFeedback = feedback.selectList(new LambdaQueryWrapper<EvaluationFeedbackEntity>()
                .eq(EvaluationFeedbackEntity::getSpaceId, experiment.getSpaceId()).in(EvaluationFeedbackEntity::getOnlineAssignmentId, ids)
                .le(EvaluationFeedbackEntity::getCreatedAt, cutoff).orderByAsc(EvaluationFeedbackEntity::getId));
        return new Snapshot(rows, chosenAttempts, chosenResults, chosenMetrics, chosenEvidence, chosenLinks, chosenFeedback, eventRows(experiment, cutoff));
    }
    private List<OnlineExperimentEventEntity> eventRows(OnlineExperimentEntity experiment, LocalDateTime cutoff) {
        return events.selectList(new LambdaQueryWrapper<OnlineExperimentEventEntity>().eq(OnlineExperimentEventEntity::getExperimentId, experiment.getId())
                .le(OnlineExperimentEventEntity::getCreatedAt, cutoff).orderByAsc(OnlineExperimentEventEntity::getId));
    }
    record Snapshot(List<OnlineAssignmentEntity> assignments, List<OnlineEvaluationAttemptEntity> attempts,
            List<EvaluationResultEntity> results, List<EvaluationMetricEntity> metrics, List<EvaluationEvidenceReferenceEntity> evidence,
            List<EvaluationMetricEvidenceEntity> links, List<EvaluationFeedbackEntity> feedback, List<OnlineExperimentEventEntity> events) {
        /** 私有诊断、Trace和完整binding不进入报告冻结输入，仍以内容摘要留存选择身份。 */
        JsonNode frozen() {
            var source = Map.of("assignments", freezeRows(assignments), "attempts", freezeRows(attempts), "results", freezeRows(results),
                    "metrics", freezeRows(metrics), "evidence", freezeRows(evidence), "links", freezeRows(links), "feedback", freezeRows(feedback), "events", freezeRows(events));
            var envelope = OnlineProtocolUtils.object(OnlineProtocolUtils.canonical("online.report-facts", source));
            var payload = envelope.path("payload");
            for (String collection : List.of("assignments", "results", "evidence", "feedback", "events")) {
                for (var value : payload.path(collection)) {
                    var row = (ObjectNode) value;
                    for (String field : List.of("bindingJson", "detailsJson", "locatorJson", "comment", "safetyReason", "factsJson", "detailJson")) {
                        if (row.has(field)) { row.put(field + "Hash", StableSnapshotUtils.sha256Utf8(row.path(field).toString())); row.remove(field); }
                    }
                    row.remove(List.of("traceId", "spanId", "updatedAt", "lastObservedAt"));
                }
            }
            return envelope;
        }
        private static List<Map<String, Object>> freezeRows(List<?> rows) {
            return rows.stream().map(row -> {
                var fields = new LinkedHashMap<String, Object>();
                for (Class<?> type = row.getClass(); type != Object.class; type = type.getSuperclass()) {
                    for (var field : type.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers())) { continue; }
                        try {
                            field.setAccessible(true); Object value = field.get(row);
                            if (value instanceof LocalDateTime time) { value = time.toString(); }
                            else if (value instanceof BigDecimal decimal) { value = decimal.stripTrailingZeros().toPlainString(); }
                            else if (value instanceof BigInteger integer) { value = integer.toString(); }
                            fields.put(field.getName(), value);
                        } catch (IllegalAccessException impossible) { throw new IllegalStateException("报告字段不可读取", impossible); }
                    }
                }
                return (Map<String, Object>) fields;
            }).toList();
        }
    }
}
