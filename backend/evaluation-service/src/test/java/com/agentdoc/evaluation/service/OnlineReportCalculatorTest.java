package com.agentdoc.evaluation.service;

import com.agentdoc.common.feign.vo.OnlineReportExecutionFactVO;
import com.agentdoc.common.feign.vo.OnlineReportTaskFactVO;
import com.agentdoc.common.feign.vo.OnlineReportUsageVO;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.feign.vo.OnlineApprovalSourceVO;
import com.agentdoc.common.feign.vo.ChangeRequestFeedbackSnapshotVO;
import com.agentdoc.common.utils.OnlineApprovalDifferenceUtils;
import com.agentdoc.evaluation.pojo.dto.OnlineQualityFeedbackDTO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.pojo.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import static org.assertj.core.api.Assertions.*;

class OnlineReportCalculatorTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 12, 10, 0);
    private final OnlineExperimentEntity experiment = experiment();
    private static OnlineExperimentEntity experiment() { var row = new OnlineExperimentEntity(); row.setId(11L); row.setSpaceId(10L); row.setManifestHash("a".repeat(64)); return row; }
    private JsonNode manifest(boolean human, String mode) {
        return JsonUtils.parse("""
                {"documentIds":["41","42"],"bucketProtocol":{"seed":"00000000000000000000000000000000","weight":5000},
                 "analysisPlan":{"mode":"%s","humanQualityRequired":%s},"ruleBindings":[{"binding":{"ruleKey":"quality",
                  "role":"PRIMARY","evaluatorVersionId":"21","metricKey":"text.assertion.pass-ratio","valueType":"NUMBER","unit":"ratio",
                  "comparison":"GE","threshold":1,"expectedBindings":[{"documentId":"41","expectedJson":{}},{"documentId":"42","expectedJson":{}}]}}]}
                """.formatted(mode, human), JsonNode.class);
    }
    private OnlineAssignmentEntity assignment(long id, long document, long sequence) {
        var row = new OnlineAssignmentEntity(); row.setId(id); row.setSpaceId(10L); row.setExperimentId(11L); row.setTaskId(id + 100); row.setExecutionId(id + 200);
        row.setDocumentId(document); row.setAcceptedSequence(sequence); row.setVariant(document == 41 ? "BASELINE" : "CANDIDATE"); row.setBindingSchemaVersion(2); row.setBindingHash("b".repeat(64)); row.setCreatedAt(TIME); return row;
    }
    private OnlineReportSnapshotService.Snapshot snapshot(List<OnlineAssignmentEntity> rows, BigDecimal value) {
        var attempts = new ArrayList<OnlineEvaluationAttemptEntity>(); var results = new ArrayList<EvaluationResultEntity>(); var metrics = new ArrayList<EvaluationMetricEntity>();
        var evidence = new ArrayList<EvaluationEvidenceReferenceEntity>(); var links = new ArrayList<EvaluationMetricEvidenceEntity>();
        for (var row : rows) {
            var attempt = new OnlineEvaluationAttemptEntity(); attempt.setId(row.getId() + 300); attempt.setAssignmentId(row.getId()); attempt.setRuleKey("quality");
            attempt.setAttemptNo(1); attempt.setEvaluatorVersionId(21L); attempt.setExpectedHash(OnlineProtocolUtils.hash("online.expected", JsonUtils.parse("{}", JsonNode.class)));
            attempt.setResultId(row.getId() + 400); attempts.add(attempt);
            var result = new EvaluationResultEntity(); result.setId(attempt.getResultId()); result.setOnlineAssignmentId(row.getId()); result.setOnlineEvaluationAttemptId(attempt.getId());
            result.setTaskId(row.getTaskId()); result.setExecutionId(row.getExecutionId()); result.setStatus("PASSED"); results.add(result);
            var metric = new EvaluationMetricEntity(); metric.setId(row.getId() + 500); metric.setOnlineEvaluationAttemptId(attempt.getId()); metric.setEvaluationResultId(result.getId());
            metric.setOnlineAssignmentId(row.getId()); metric.setTaskId(row.getTaskId()); metric.setExecutionId(row.getExecutionId()); metric.setSpaceId(10L); metric.setEvaluatorVersionId(21L);
            metric.setSource("EVALUATOR"); metric.setProducerId(result.getId()); metric.setContractVersion(1); metric.setMetricKey("text.assertion.pass-ratio"); metric.setValueType("NUMBER"); metric.setUnit("ratio"); metric.setNumericValue(value); metrics.add(metric);
            var ref = new EvaluationEvidenceReferenceEntity(); ref.setId(row.getId() + 600); ref.setOnlineEvaluationAttemptId(attempt.getId()); ref.setResultId(result.getId()); ref.setOnlineAssignmentId(row.getId());
            ref.setTaskId(row.getTaskId()); ref.setExecutionId(row.getExecutionId()); ref.setSpaceId(10L); ref.setContentHash("c".repeat(64)); evidence.add(ref);
            var link = new EvaluationMetricEvidenceEntity(); link.setMetricId(metric.getId()); link.setEvidenceReferenceId(ref.getId()); links.add(link);
        }
        return new OnlineReportSnapshotService.Snapshot(rows, attempts, results, metrics, evidence, links, List.of(), List.of());
    }
    private JsonNode report(JsonNode manifest, OnlineReportSnapshotService.Snapshot snapshot, String status) {
        var executions = snapshot.assignments().stream().map(row -> new OnlineReportExecutionFactVO(row.getTaskId().toString(), row.getId().toString(), row.getBindingHash(), "AVAILABLE",
                row.getExecutionId().toString(), status, TIME.toString(), TIME.plusSeconds(1).toString(), TIME.plusSeconds(2).toString(), null, null, null)).toList();
        return OnlineReportCalculator.calculate(experiment, manifest, 100, TIME.plusHours(1), snapshot, List.of(), executions, List.of());
    }
    @Test void validZeroIsFailureAndMissingTaskAndLedgerAreNotRemovedOrInventedZero() {
        var data = report(manifest(false, "ENGINEERING"), snapshot(List.of(assignment(1, 41, 1)), BigDecimal.ZERO), "COMPLETED");
        var group = data.path("groups").path("BASELINE");
        assertThat(group.path("assignedTaskCount").asInt()).isEqualTo(1); assertThat(group.path("primaryFailDocumentCount").asInt()).isEqualTo(1);
        assertThat(group.path("primaryValidRatio").asText()).isEqualTo("0.000000000000"); assertThat(group.path("ledgerUnknownCount").asInt()).isEqualTo(1);
        assertThat(group.has("ledgerTokens")).isFalse(); assertThat(data.path("decisionEligibility").path("comparisonAllowed").asBoolean()).isFalse();
    }
    @Test void firstAcceptedDocumentIsPrimaryEvenWhenLaterTaskPassesAndRowsAreReordered() {
        var first = assignment(1, 41, 1); var later = assignment(2, 41, 2); var snapshot = snapshot(List.of(later, first), BigDecimal.ONE);
        snapshot.metrics().get(1).setNumericValue(BigDecimal.ZERO);
        var report = report(manifest(false, "DESCRIPTIVE"), snapshot, "COMPLETED");
        var group = report.path("groups").path("BASELINE");
        assertThat(group.path("assignedTaskCount").asInt()).isEqualTo(2); assertThat(group.path("participatingDocumentCount").asInt()).isEqualTo(1);
        assertThat(group.path("primaryFailDocumentCount").asInt()).isEqualTo(1); assertThat(group.path("primaryPassDocumentCount").asInt()).isZero();
        assertThat(report.path("ruleOutcomes").path("quality").path("FAIL").asInt()).isEqualTo(1);
        assertThat(report.path("ruleOutcomes").path("quality").has("PASS")).isFalse();
        assertThat(report.path("taskRuleOutcomes").path("quality").path("PASS").asInt()).isEqualTo(1);
    }
    @Test void absentMetricsAndBrokenEvidenceAreMissingAndFailureTerminalIsFailWithoutMetric() {
        var row = assignment(1, 41, 1); var source = snapshot(List.of(row), null);
        assertThat(report(manifest(false, "DESCRIPTIVE"), source, "COMPLETED").path("groups").path("BASELINE").path("primaryMissingDocumentCount").asInt()).isEqualTo(1);
        assertThat(report(manifest(false, "DESCRIPTIVE"), source, "FAILED").path("groups").path("BASELINE").path("primaryFailDocumentCount").asInt()).isEqualTo(1);
        source.metrics().getFirst().setNumericValue(BigDecimal.ONE); source.evidence().getFirst().setExecutionId(999L);
        assertThat(report(manifest(false, "DESCRIPTIVE"), source, "COMPLETED").path("groups").path("BASELINE").path("primaryMissingDocumentCount").asInt()).isEqualTo(1);
    }
    @Test void effectiveFalseIsValidFailureAndSkippedNeverProducesQualityZero() {
        var source = snapshot(List.of(assignment(1, 41, 1)), null); var manifest = manifest(false, "DESCRIPTIVE");
        var binding = (ObjectNode) manifest.path("ruleBindings").get(0).path("binding"); binding.put("valueType", "BOOLEAN"); binding.put("threshold", true);
        source.metrics().getFirst().setValueType("BOOLEAN"); source.metrics().getFirst().setBooleanValue(false);
        assertThat(report(manifest, source, "COMPLETED").path("groups").path("BASELINE").path("primaryFailDocumentCount").asInt()).isEqualTo(1);
        source.results().getFirst().setStatus("SKIPPED");
        var skipped = report(manifest, source, "COMPLETED"); assertThat(skipped.path("ruleOutcomes").path("quality").path("NOT_APPLICABLE").asInt()).isEqualTo(1);
        assertThat(skipped.path("groups").path("BASELINE").path("primaryMissingDocumentCount").asInt()).isEqualTo(1);
    }
    @Test void emptyGroupHasNullRatiosAndIntervalsAndNoSafetyExceptionFromOrdinaryLowQuality() {
        var data = report(manifest(true, "DESCRIPTIVE"), snapshot(List.of(), BigDecimal.ZERO), "COMPLETED");
        assertThat(data.path("groups").path("BASELINE").path("primaryValidRatio").isNull()).isTrue();
        assertThat(data.path("groups").path("BASELINE").path("primaryConfidenceInterval").path("lower").isNull()).isTrue();
        assertThat(data.path("decisionEligibility").path("safetyRejectedAllowed").asBoolean()).isFalse();
        assertThat(data.path("decisionEligibility").path("reasonCodes").toString()).contains("BLINDED_QUALITY_COVERAGE_INSUFFICIENT", "RUNTIME_SRM_NOT_ENOUGH_UNITS");
    }
    @Test void snapshotRemovesPrivateDiagnosticsAndTraceButKeepsHashesAndSourceIdentities() {
        var source = snapshot(List.of(assignment(1, 41, 1)), BigDecimal.ONE); source.assignments().getFirst().setBindingJson("sensitive binding");
        source.results().getFirst().setDetailsJson("private diagnostics"); source.results().getFirst().setTraceId("trace-sensitive");
        String frozen = source.frozen().toString(); assertThat(frozen).doesNotContain("sensitive binding", "private diagnostics", "trace-sensitive").contains("bindingJsonHash", "detailsJsonHash", "onlineEvaluationAttemptId");
    }
    private JsonNode largeReport(boolean human, int missingPerGroup, String mode) {
        return largeReport(human, missingPerGroup, mode, 0, false);
    }
    private JsonNode largeReport(boolean human, int missingPerGroup, String mode, int blindedPerGroup, boolean correctToNonBlinded) {
        var rows = new ArrayList<OnlineAssignmentEntity>();
        for (int i = 1; i <= 100; i++) { var row = assignment(i, 1000 + i, i); row.setVariant(i <= 50 ? "BASELINE" : "CANDIDATE"); rows.add(row); }
        var manifest = (ObjectNode) manifest(human, mode); manifest.set("documentIds", JsonUtils.parse(JsonUtils.toJson(rows.stream().map(row -> row.getDocumentId().toString()).toList()), JsonNode.class));
        var binding = (ObjectNode) manifest.path("ruleBindings").get(0).path("binding");
        binding.set("expectedBindings", JsonUtils.parse(JsonUtils.toJson(rows.stream().map(row -> Map.of("documentId", row.getDocumentId().toString(), "expectedJson", Map.of())).toList()), JsonNode.class));
        var snapshot = snapshot(rows, BigDecimal.ONE);
        var feedback = new ArrayList<EvaluationFeedbackEntity>();
        for (int i = 0; i < rows.size(); i++) {
            if (i % 50 < blindedPerGroup) { feedback.add(quality(rows.get(i), rows.get(i).getId() + 10000, "BLINDED", 0, null)); }
        }
        if (correctToNonBlinded) { feedback.add(quality(rows.getFirst(), 30001L, "NON_BLINDED", 4, 10001L)); }
        snapshot = new OnlineReportSnapshotService.Snapshot(snapshot.assignments(), snapshot.attempts(), snapshot.results(), snapshot.metrics(), snapshot.evidence(), snapshot.links(), feedback, snapshot.events());
        for (int i = 0; i < 100; i++) { if (i % 50 < missingPerGroup) { snapshot.metrics().get(i).setNumericValue(null); } }
        var executions = rows.stream().map(row -> new OnlineReportExecutionFactVO(row.getTaskId().toString(), row.getId().toString(), row.getBindingHash(), "AVAILABLE", row.getExecutionId().toString(), "COMPLETED", TIME.toString(), TIME.plusSeconds(1).toString(), TIME.plusSeconds(2).toString(), null, null, null)).toList();
        var tasks = rows.stream().map(row -> new OnlineReportTaskFactVO(row.getTaskId().toString(), row.getId().toString(), row.getBindingHash(), "AVAILABLE", "COMPLETED", row.getExecutionId().toString(), TIME.toString(), TIME.plusSeconds(1).toString(), TIME.plusSeconds(2).toString(), null, null, null, null)).toList();
        return OnlineReportCalculator.calculate(experiment, manifest, 100, TIME.plusHours(1), snapshot, tasks, executions, List.of());
    }
    private EvaluationFeedbackEntity quality(OnlineAssignmentEntity assignment, long id, String blindness, int rubric, Long predecessor) {
        var input = new OnlineOriginalInputVO(assignment.getTaskId().toString(), assignment.getExecutionId().toString(), "10", "20", "11", assignment.getId().toString(), assignment.getBindingHash(), assignment.getDocumentId().toString(), "FORMAL", 3L, "b".repeat(64), true);
        var quality = new OnlineQualityFeedbackDTO(assignment.getId().toString(), "ORIGINAL_TEXT", assignment.getExecutionId().toString(), "c".repeat(64), rubric, blindness, predecessor == null ? null : predecessor.toString(), List.of(), false, null);
        var row = new EvaluationFeedbackEntity(); row.setId(id); row.setOnlineAssignmentId(assignment.getId()); row.setTaskId(assignment.getTaskId()); row.setExecutionId(assignment.getExecutionId()); row.setSpaceId(10L); row.setCreatedBy(502L); row.setSourceType("MANUAL");
        row.setOriginalEvidenceId(assignment.getExecutionId()); row.setOriginalEvidenceHash("c".repeat(64)); row.setRubricScore(rubric); row.setBlindness(blindness); row.setSupersedesFeedbackId(predecessor); row.setReasonCodesJson("[]"); row.setSeriousSafetyEvent(false); row.setLabel("ACCEPTED"); row.setScore(quality.normalizedScore());
        var facts = new LinkedHashMap<String, Object>(Map.of("qualitySchemaVersion", 2, "input", input, "originalEvidenceType", "ORIGINAL_TEXT", "originalIdentityHash", "f".repeat(64), "declaredBlindness", blindness));
        var payload = new LinkedHashMap<String, Object>(Map.ofEntries(Map.entry("feedbackId", Long.toString(id)), Map.entry("input", input), Map.entry("quality", quality), Map.entry("originalIdentityHash", "f".repeat(64)), Map.entry("effectiveBlindness", blindness), Map.entry("label", "ACCEPTED"), Map.entry("score", quality.normalizedScore().toPlainString()), Map.entry("comment", ""), Map.entry("actorId", "502")));
        String domain = "online.quality-feedback";
        if ("BLINDED".equals(blindness)) { facts.put("reviewReceiptId", Long.toString(id + 1000)); facts.put("reviewReceiptHash", "a".repeat(64)); payload.put("reviewReceiptId", Long.toString(id + 1000)); payload.put("reviewReceiptHash", "a".repeat(64)); domain = "online.quality-feedback-with-review-proof"; }
        row.setFactsJson(JsonUtils.toJson(facts)); row.setSourceHash(OnlineProtocolUtils.hash(domain, payload)); assertThat(OnlineQualityFeedbackFacts.valid(row)).isTrue(); return row;
    }
    @Test void blindedZeroCountsAtEightyPercentAndExplicitNonBlindedCorrectionCannotFillCoverage() {
        var allowed = largeReport(true, 0, "DESCRIPTIVE", 40, false); assertThat(allowed.path("decisionEligibility").path("comparisonAllowed").asBoolean()).isTrue();
        assertThat(allowed.path("groups").path("BASELINE").path("qualityByBlindness").path("BLINDED").path("meanRubricScore").asText()).isEqualTo("0.000000000000");
        var corrected = largeReport(true, 0, "DESCRIPTIVE", 40, true); var group = corrected.path("groups").path("BASELINE");
        assertThat(group.path("blindedDocumentCount").asInt()).isEqualTo(39); assertThat(group.path("nonBlindedDocumentCount").asInt()).isEqualTo(1);
        assertThat(group.path("qualityByBlindness").path("NON_BLINDED").path("meanRubricScore").asText()).isEqualTo("4.000000000000");
        assertThat(corrected.path("decisionEligibility").path("comparisonAllowed").asBoolean()).isFalse(); assertThat(corrected.path("feedbackStrata").path("FIRST").asInt()).isEqualTo(80); assertThat(corrected.path("feedbackStrata").path("CORRECTION").asInt()).isEqualTo(1);
    }
    @Test void eightyPercentIsExactInclusiveAndMissingCannotBeUsedToImproveComparison() {
        var allowed = largeReport(false, 10, "DESCRIPTIVE"); assertThat(allowed.path("decisionEligibility").path("comparisonAllowed").asBoolean()).isTrue();
        assertThat(allowed.path("groups").path("BASELINE").path("primaryValidCoverage").asText()).isEqualTo("0.800000000000");
        var insufficient = largeReport(false, 11, "DESCRIPTIVE"); assertThat(insufficient.path("decisionEligibility").path("comparisonAllowed").asBoolean()).isFalse();
        assertThat(insufficient.path("decisionEligibility").path("reasonCodes").toString()).contains("PRIMARY_COVERAGE_INSUFFICIENT");
        assertThat(largeReport(false, 0, "ENGINEERING").path("decisionEligibility").path("reasonCodes").toString()).contains("ENGINEERING_MODE");
        assertThat(largeReport(true, 0, "DESCRIPTIVE").path("decisionEligibility").path("reasonCodes").toString()).contains("BLINDED_QUALITY_COVERAGE_INSUFFICIENT");
    }
    @Test void frozenLedgerKeepsCurrencyAndEstimationSeparateAndRespectsObservationCutoff() {
        var rows = List.of(assignment(1, 41, 1), assignment(2, 42, 2)); var snapshot = snapshot(rows, BigDecimal.ONE);
        var executions = rows.stream().map(row -> new OnlineReportExecutionFactVO(row.getTaskId().toString(), row.getId().toString(), row.getBindingHash(), "AVAILABLE", row.getExecutionId().toString(), "COMPLETED", TIME.toString(), TIME.plusSeconds(1).toString(), TIME.plusSeconds(2).toString(), null, null, null)).toList();
        var tasks = new ArrayList<OnlineReportTaskFactVO>();
        for (var row : rows) { var usage = new OnlineReportUsageVO(row.getExecutionId(), 20L, 1L, "2", "4", row.getId() == 1 ? "USD" : "CNY", 1, TIME.toString(), 1000000L, true, 0L, false, 1000000L, false);
            tasks.add(new OnlineReportTaskFactVO(row.getTaskId().toString(), row.getId().toString(), row.getBindingHash(), "AVAILABLE", "COMPLETED", row.getExecutionId().toString(), TIME.toString(), null, null, row.getId().toString(), OnlineProtocolUtils.hash("online.report-ledger", usage), TIME.plusMinutes(row.getId() == 1 ? 1 : 61).toString(), usage)); }
        var report = OnlineReportCalculator.calculate(experiment, manifest(false, "DESCRIPTIVE"), 100, TIME.plusHours(1), snapshot, tasks, executions, List.of());
        assertThat(report.path("budgetByCurrency").path("USD").asText()).isEqualTo("6.000000000000"); assertThat(report.path("budgetByCurrency").has("CNY")).isFalse();
        assertThat(report.path("groups").path("BASELINE").path("estimatedTokenLedgerCount").asInt()).isEqualTo(1);
        assertThat(report.path("groups").path("CANDIDATE").path("ledgerUnknownCount").asInt()).isEqualTo(1);
        assertThat(report.path("groups").path("BASELINE").path("firstAssignmentResource").path("meanCostByCurrency").path("USD").asText()).isEqualTo("6.000000000000");
        assertThat(report.path("groups").path("BASELINE").path("firstAssignmentResource").path("endToEndLatencyMeanMillis").asText()).isEqualTo("2000.000000000000");
    }
    private EvaluationFeedbackEntity approval(OnlineAssignmentEntity assignment, long id, boolean merged) {
        var input = new OnlineOriginalInputVO(assignment.getTaskId().toString(), assignment.getExecutionId().toString(), "10", "20", "11", assignment.getId().toString(), assignment.getBindingHash(), assignment.getDocumentId().toString(), "FORMAL", 3L, "b".repeat(64), true);
        var source = new OnlineApprovalSourceVO(input, "901", merged ? "MERGED" : "APPROVED", "c".repeat(64), "d".repeat(64), 3L, null, merged ? 4L : null, merged ? "502" : null, merged ? TIME.toString() : null, merged ? null : "NOT_MERGED");
        var difference = OnlineApprovalDifferenceUtils.signed(source, merged ? null : "NOT_MERGED", merged ? "a".repeat(64) : null, merged ? "f".repeat(64) : null, merged ? 1 : null, merged ? 2 : null, merged ? 1 : null, merged ? new BigDecimal("0.25") : null);
        var captured = new ChangeRequestFeedbackSnapshotVO(901L, 10L, assignment.getTaskId(), assignment.getExecutionId(), source.status(), "EDITED", "a".repeat(64), 502L, TIME, 1, null, difference);
        var row = new EvaluationFeedbackEntity(); row.setId(id); row.setOnlineAssignmentId(assignment.getId()); row.setTaskId(assignment.getTaskId()); row.setExecutionId(assignment.getExecutionId()); row.setSpaceId(10L); row.setSourceType("CHANGE_REQUEST"); row.setSourceBusinessId("901"); row.setOriginalEvidenceId(901L); row.setOriginalEvidenceHash("c".repeat(64)); row.setLabel("ACCEPTED"); row.setSourceHash(OnlineApprovalDifferenceUtils.feedbackHash(captured));
        row.setFactsJson(JsonUtils.toJson(Map.of("status", source.status(), "resolutionType", "EDITED", "reviewCommentHash", "a".repeat(64), "reviewedBy", 502L, "reviewedAt", TIME.toString(), "revisionNo", 1, "onlineApprovalDifference", difference))); return row;
    }
    @Test void approvalUsesLatestImportedRequestAndDoesNotOverwriteOriginalQualityOrInventRejections() {
        var assignment = assignment(1, 41, 1); assignment.setAgentId(20L);
        var source = snapshot(List.of(assignment), BigDecimal.ZERO); var approved = approval(assignment, 9011, false); var merged = approval(assignment, 9012, true);
        var selected = new OnlineReportSnapshotService.Snapshot(source.assignments(), source.attempts(), source.results(), source.metrics(), source.evidence(), source.links(), List.of(approved, merged), source.events());
        var report = report(manifest(false, "DESCRIPTIVE"), selected, "COMPLETED"); var business = report.path("approvalFeedback").path("groups").path("BASELINE");
        assertThat(business.path("selectedRequestCount").asInt()).isEqualTo(1); assertThat(business.path("status_MERGED").asInt()).isEqualTo(1); assertThat(business.path("status_APPROVED").asInt()).isZero();
        assertThat(business.path("meanHumanModificationRatio").asText()).isEqualTo("0.250000000000");
        assertThat(report.path("groups").path("BASELINE").path("primaryFailDocumentCount").asInt()).isEqualTo(1); assertThat(report.path("groups").path("BASELINE").path("unratedDocumentCount").asInt()).isEqualTo(1);
        merged.setSourceHash("f".repeat(64)); var invalid = report(manifest(false, "DESCRIPTIVE"), selected, "COMPLETED").path("approvalFeedback").path("groups").path("BASELINE");
        assertThat(invalid.path("unknownRequestCount").asInt()).isEqualTo(1); assertThat(invalid.path("comparableRequestCount").asInt()).isZero(); assertThat(invalid.path("unrecordedAssignmentCount").asInt()).isEqualTo(1);
    }
}
