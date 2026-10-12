package com.agentdoc.evaluation.service;

import static com.agentdoc.evaluation.constant.OnlineExperimentConstant.*;
import static com.agentdoc.evaluation.constant.OnlineReportConstant.*;
import com.agentdoc.common.feign.vo.OnlineReportExecutionFactVO;
import com.agentdoc.common.feign.vo.OnlineReportTaskFactVO;
import com.agentdoc.common.feign.vo.ChangeRequestFeedbackSnapshotVO;
import com.agentdoc.common.feign.vo.OnlineApprovalDifferenceVO;
import com.agentdoc.common.utils.OnlineApprovalDifferenceUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.pojo.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** ONLINE_V1纯计算；独立文档与运行Task分母分别统计，不挑选最好一次结果。 */
public final class OnlineReportCalculator {
    public static final String IMPLEMENTATION = "online-report-v1";
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED", "TIMED_OUT", "CANCELED");
    private OnlineReportCalculator() { }

    static JsonNode calculate(OnlineExperimentEntity experiment, JsonNode manifest, long sequence, LocalDateTime cutoff,
            OnlineReportSnapshotService.Snapshot snapshot, List<OnlineReportTaskFactVO> tasks,
            List<OnlineReportExecutionFactVO> executions, List<String> sourceIssues) {
        var taskMap = tasks.stream().collect(Collectors.toMap(OnlineReportTaskFactVO::taskId, item -> item));
        var executionMap = executions.stream().collect(Collectors.toMap(OnlineReportExecutionFactVO::taskId, item -> item));
        var assignmentMap = snapshot.assignments().stream().collect(Collectors.toMap(OnlineAssignmentEntity::getId, item -> item));
        var results = snapshot.results().stream().collect(Collectors.toMap(EvaluationResultEntity::getId, item -> item));
        var metrics = snapshot.metrics().stream().collect(Collectors.groupingBy(EvaluationMetricEntity::getOnlineEvaluationAttemptId));
        var evidence = snapshot.evidence().stream().collect(Collectors.toMap(EvaluationEvidenceReferenceEntity::getId, item -> item));
        var links = snapshot.links().stream().collect(Collectors.groupingBy(EvaluationMetricEvidenceEntity::getMetricId));
        var attempts = new HashMap<String, OnlineEvaluationAttemptEntity>();
        for (var attempt : snapshot.attempts()) {
            String key = attempt.getAssignmentId() + ":" + attempt.getRuleKey(); var previous = attempts.get(key);
            if (previous == null || attempt.getAttemptNo() > previous.getAttemptNo()) { attempts.put(key, attempt); }
        }
        var feedback = snapshot.feedback().stream().filter(item -> "MANUAL".equals(item.getSourceType()) && item.getRubricScore() != null)
                .collect(Collectors.groupingBy(EvaluationFeedbackEntity::getOnlineAssignmentId));
        var subjects = new LinkedHashMap<Long, OnlineAssignmentEntity>();
        var groupRows = new LinkedHashMap<String, ObjectNode>();
        for (String variant : List.of("BASELINE", "CANDIDATE")) { groupRows.put(variant, node()); }
        var exceptions = new TreeSet<>(sourceIssues);
        var budgets = new TreeMap<String, BigDecimal>();
        var ruleCounts = new LinkedHashMap<String, Map<String, Long>>();
        var documentRuleCounts = new LinkedHashMap<String, Map<String, Long>>();
        var assignmentRuleOutcomes = new HashMap<Long, Map<String, String>>();
        for (var rule : manifest.path("ruleBindings")) {
            String key = rule.path("binding").path("ruleKey").asText();
            ruleCounts.put(key, new TreeMap<>()); documentRuleCounts.put(key, new TreeMap<>());
        }
        int rangeCandidate = 0;
        for (var document : manifest.path("documentIds")) {
            String variant = OnlineProtocolUtils.variant(OnlineProtocolUtils.bucket(experiment.getId().toString(), document.asText(),
                    manifest.path("bucketProtocol").path("seed").asText()), manifest.path("bucketProtocol").path("weight").asInt());
            increment(groupRows.get(variant), "rangeDocumentCount"); if (variant.equals("CANDIDATE")) { rangeCandidate++; }
        }
        for (var assignment : snapshot.assignments()) {
            subjects.merge(assignment.getDocumentId(), assignment, (first, next) -> first.getAcceptedSequence() < next.getAcceptedSequence() ? first : next);
            var group = groupRows.get(assignment.getVariant());
            if (group == null || !Integer.valueOf(2).equals(assignment.getBindingSchemaVersion())) { exceptions.add("ASSIGNMENT_IDENTITY_INVALID"); continue; }
            increment(group, "assignedTaskCount"); var task = taskMap.get(assignment.getTaskId().toString()); var execution = executionMap.get(assignment.getTaskId().toString());
            if (task != null && "AVAILABLE".equals(task.state()) && task.createdAt() != null && !time(task.createdAt()).isAfter(cutoff)) { increment(group, "createdTaskCount"); }
            String status = executionStatus(execution, cutoff);
            if (execution != null && execution.startedAt() != null && !time(execution.startedAt()).isAfter(cutoff)) { increment(group, "startedTaskCount"); }
            if (TERMINAL.contains(status)) { increment(group, "terminalTaskCount"); }
            String statusKey = switch (status) { case "COMPLETED" -> "completedTaskCount"; case "FAILED" -> "failedTaskCount";
                case "TIMED_OUT" -> "timedOutTaskCount"; case "CANCELED" -> "userCanceledTaskCount";
                case "RUNNING", "PENDING", "CREATED" -> "pendingTaskCount"; default -> "unknownTaskCount"; };
            increment(group, statusKey);
            if (task == null || !"AVAILABLE".equals(task.state())) { increment(group, "taskFactMissingCount"); exceptions.add("TASK_FACT_MISSING"); }
            if (status.equals("UNKNOWN")) { exceptions.add("EXECUTION_UNKNOWN"); }
            resource(group, task, execution, cutoff, budgets);
            var outcomes = new LinkedHashMap<String, String>();
            for (var frozen : manifest.path("ruleBindings")) {
                var binding = frozen.path("binding"); String rule = binding.path("ruleKey").asText();
                var attempt = attempts.get(assignment.getId() + ":" + rule);
                String outcome = ruleOutcome(assignment, binding, attempt, results, metrics, evidence, links);
                outcomes.put(rule, outcome); ruleCounts.get(rule).merge(outcome, 1L, Long::sum);
            }
            assignmentRuleOutcomes.put(assignment.getId(), outcomes);
        }
        var strata = new TreeMap<String, Long>(); var safety = new ArrayList<String>();
        for (var assignment : subjects.values()) {
            var group = groupRows.get(assignment.getVariant()); if (group == null) { continue; }
            increment(group, "participatingDocumentCount");
            String status = executionStatus(executionMap.get(assignment.getTaskId().toString()), cutoff);
            boolean failed = Set.of("FAILED", "TIMED_OUT").contains(status), missing = !"COMPLETED".equals(status);
            var outcomes = assignmentRuleOutcomes.get(assignment.getId());
            if (outcomes == null) { outcomes = Map.of(); }
            for (var frozen : manifest.path("ruleBindings")) {
                String key = frozen.path("binding").path("ruleKey").asText();
                String outcome = outcomes.getOrDefault(key, "MISSING");
                documentRuleCounts.get(key).merge(outcome, 1L, Long::sum);
                var groupRules = group.has("ruleOutcomes") ? (ObjectNode) group.get("ruleOutcomes") : node();
                var rule = groupRules.has(key) ? (ObjectNode) groupRules.get(key) : node();
                increment(rule, outcome); rule.put("validCount", count(rule, "PASS") + count(rule, "FAIL"));
                rule.put("passRatio", ratio(count(rule, "PASS"), count(rule, "validCount")));
                groupRules.set(key, rule); group.set("ruleOutcomes", groupRules);
            }
            for (var frozen : manifest.path("ruleBindings")) {
                if (!Set.of("PRIMARY", "GUARDRAIL").contains(frozen.path("binding").path("role").asText())) { continue; }
                String outcome = outcomes.get(frozen.path("binding").path("ruleKey").asText());
                failed |= "FAIL".equals(outcome); missing |= !Set.of("PASS", "FAIL").contains(outcome);
            }
            increment(group, failed ? "primaryFailDocumentCount" : missing ? "primaryMissingDocumentCount" : "primaryPassDocumentCount");
            var rows = feedback.getOrDefault(assignment.getId(), List.of());
            var tail = qualityTail(assignment, rows);
            var execution = executionMap.get(assignment.getTaskId().toString());
            var firstResources = group.has("firstAssignmentResource") ? (ObjectNode) group.get("firstAssignmentResource") : node();
            resource(firstResources, taskMap.get(assignment.getTaskId().toString()), execution, cutoff, new TreeMap<>());
            group.set("firstAssignmentResource", firstResources);
            boolean originalRecorded = tail != null || execution != null && execution.originalTextCapturedAt() != null && !time(execution.originalTextCapturedAt()).isAfter(cutoff)
                    && execution.originalTextIdentityHash() != null && execution.originalTextIdentityHash().matches("[0-9a-f]{64}");
            increment(group, originalRecorded ? "originalEvidenceRecordedDocumentCount" : "originalEvidenceUnknownDocumentCount");
            if (originalRecorded && tail == null) { increment(group, "originalEvidenceUnratedDocumentCount"); }
            for (var row : rows) { strata.merge(row.getSupersedesFeedbackId() == null ? "FIRST" : "CORRECTION", 1L, Long::sum); }
            if (tail == null) { increment(group, "unratedDocumentCount"); }
            else {
                String blindness = tail.getBlindness();
                String key = switch (blindness) { case "BLINDED" -> "blindedDocumentCount"; case "NON_BLINDED" -> "nonBlindedDocumentCount"; default -> "unknownBlindnessDocumentCount"; };
                increment(group, key); strata.merge(blindness, 1L, Long::sum);
                var quality = group.has("qualityByBlindness") ? (ObjectNode) group.get("qualityByBlindness") : node();
                var scores = quality.has(blindness) ? (ObjectNode) quality.get(blindness) : node();
                increment(scores, "documentCount"); scores.put("rubricScoreSum", count(scores, "rubricScoreSum") + tail.getRubricScore());
                scores.put("meanRubricScore", ratio(count(scores, "rubricScoreSum"), count(scores, "documentCount")));
                scores.put("meanNormalizedScore", ratio(count(scores, "rubricScoreSum"), count(scores, "documentCount") * 4));
                quality.set(blindness, scores); group.set("qualityByBlindness", quality);
            }
        }
        // 安全事件不受首文档主分析限制，仍保留所有已确认人工事实的身份。
        for (var row : snapshot.feedback()) {
            var assignment = assignmentMap.get(row.getOnlineAssignmentId());
            if (assignment != null && Objects.equals(row.getTaskId(), assignment.getTaskId()) && Objects.equals(row.getExecutionId(), assignment.getExecutionId())
                    && Objects.equals(row.getSpaceId(), assignment.getSpaceId()) && Boolean.TRUE.equals(row.getSeriousSafetyEvent()) && "MANUAL".equals(row.getSourceType())
                    && row.getSafetyReason() != null && !row.getSafetyReason().isBlank() && OnlineQualityFeedbackFacts.valid(row)) { safety.add(row.getId().toString()); }
        }
        var eligibility = node(); var blockers = new TreeSet<String>(exceptions);
        if (!"DESCRIPTIVE".equals(manifest.path("analysisPlan").path("mode").asText())) { blockers.add("ENGINEERING_MODE"); }
        int candidateSubjects = (int) subjects.values().stream().filter(item -> "CANDIDATE".equals(item.getVariant())).count();
        var runtimeSrm = OnlineExperimentStatistics.srm(subjects.size(), candidateSubjects, manifest.path("bucketProtocol").path("weight").asInt());
        var rangeSrm = OnlineExperimentStatistics.srm(manifest.path("documentIds").size(), rangeCandidate, manifest.path("bucketProtocol").path("weight").asInt());
        if (!"PASS".equals(rangeSrm.status())) { blockers.add("RANGE_SRM_" + rangeSrm.status()); }
        if (!"PASS".equals(runtimeSrm.status())) { blockers.add("RUNTIME_SRM_" + runtimeSrm.status()); }
        for (var event : snapshot.events()) {
            if (event.getReasonCode() != null && (event.getReasonCode().contains("DRIFT") || event.getReasonCode().contains("SRM")
                    || event.getReasonCode().contains("DEPENDENCY_UNAVAILABLE"))) { blockers.add("UNRESOLVED_" + event.getReasonCode()); }
        }
        for (var group : groupRows.values()) {
            long passed = count(group, "primaryPassDocumentCount"), failed = count(group, "primaryFailDocumentCount"), total = count(group, "participatingDocumentCount");
            group.put("primaryValidRatio", ratio(passed, passed + failed)); group.put("provenCoverage", ratio(passed, total));
            group.put("primaryValidCoverage", ratio(passed + failed, total));
            group.set("primaryConfidenceInterval", JsonUtils.parse(JsonUtils.toJson(OnlineExperimentStatistics.confidence((int) passed, (int) (passed + failed))), JsonNode.class));
            group.put("blindedQualityCoverage", ratio(count(group, "blindedDocumentCount"), total));
            group.put("blindedQualityRecordedOriginalCoverage", ratio(count(group, "blindedDocumentCount"), count(group, "originalEvidenceRecordedDocumentCount")));
            group.put("queueLatencyMeanMillis", ratio(count(group, "queueLatencyTotalMillis"), count(group, "queueLatencyAvailableCount")));
            group.put("executionLatencyMeanMillis", ratio(count(group, "executionLatencyTotalMillis"), count(group, "executionLatencyAvailableCount")));
            group.put("endToEndLatencyMeanMillis", ratio(count(group, "endToEndLatencyTotalMillis"), count(group, "endToEndLatencyAvailableCount")));
            var firstResources = group.has("firstAssignmentResource") ? (ObjectNode) group.get("firstAssignmentResource") : node();
            for (String kind : List.of("queue", "execution", "endToEnd")) {
                firstResources.put(kind + "LatencyMeanMillis", ratio(count(firstResources, kind + "LatencyTotalMillis"), count(firstResources, kind + "LatencyAvailableCount")));
                firstResources.put(kind + "LatencyCoverage", ratio(count(firstResources, kind + "LatencyAvailableCount"), total));
            }
            firstResources.put("ledgerCoverage", ratio(count(firstResources, "ledgerAvailableCount"), total));
            firstResources.put("currencyCoverage", ratio(count(firstResources, "currencyAvailableCount"), total));
            if (firstResources.has("budgetByCurrency")) {
                var means = node(); var currencies = firstResources.path("budgetByCurrency");
                currencies.fieldNames().forEachRemaining(currency -> {
                    long available = count(firstResources, "currencyCount_" + currency);
                    means.put(currency, available == 0 ? null : new BigDecimal(currencies.path(currency).asText()).divide(BigDecimal.valueOf(available), 12, RoundingMode.HALF_UP).toPlainString());
                });
                firstResources.set("meanCostByCurrency", means);
            }
            group.set("firstAssignmentResource", firstResources);
            if (total < MIN_DOCUMENTS_PER_GROUP) { blockers.add("DOCUMENT_SAMPLE_INSUFFICIENT"); }
            if (!covered(passed + failed, total)) { blockers.add("PRIMARY_COVERAGE_INSUFFICIENT"); }
            if (manifest.path("analysisPlan").path("humanQualityRequired").asBoolean() && !covered(count(group, "blindedDocumentCount"), total)) {
                blockers.add("BLINDED_QUALITY_COVERAGE_INSUFFICIENT");
            }
            for (String key : List.of("rangeDocumentCount", "participatingDocumentCount", "assignedTaskCount", "createdTaskCount", "startedTaskCount", "terminalTaskCount",
                    "completedTaskCount", "failedTaskCount", "timedOutTaskCount", "userCanceledTaskCount", "pendingTaskCount", "unknownTaskCount", "primaryPassDocumentCount",
                    "primaryFailDocumentCount", "primaryMissingDocumentCount", "blindedDocumentCount", "nonBlindedDocumentCount", "unknownBlindnessDocumentCount", "unratedDocumentCount",
                    "ledgerAvailableCount", "ledgerUnknownCount", "currencyAvailableCount", "queueLatencyAvailableCount", "executionLatencyAvailableCount", "endToEndLatencyAvailableCount",
                    "originalEvidenceRecordedDocumentCount", "originalEvidenceUnknownDocumentCount", "originalEvidenceUnratedDocumentCount", "taskFactMissingCount", "estimatedTokenLedgerCount", "tokenEstimationUnknownCount")) {
                if (!group.has(key)) { group.put(key, 0); }
            }
        }
        eligibility.put("comparisonAllowed", blockers.isEmpty()); eligibility.set("reasonCodes", JsonUtils.parse(JsonUtils.toJson(blockers), JsonNode.class));
        eligibility.set("confirmedSafetyFeedbackIds", JsonUtils.parse(JsonUtils.toJson(safety), JsonNode.class));
        eligibility.put("safetyRejectedAllowed", !safety.isEmpty());
        var report = node(); report.put("reportType", TYPE); report.put("schemaVersion", SCHEMA_VERSION); report.put("calculationVersion", IMPLEMENTATION);
        report.put("manifestHash", experiment.getManifestHash()); report.put("assignmentCutoffSequence", Long.toString(sequence)); report.put("observationCutoffAt", cutoff.toString());
        var windows = node(); windows.put("startedAt", text(experiment.getStartedAt())); windows.put("assignmentDeadline", text(experiment.getAssignmentDeadline()));
        windows.put("observationDeadline", text(experiment.getObservationDeadline())); windows.put("stopRequestedAt", text(experiment.getStopRequestedAt())); windows.put("stoppedAt", text(experiment.getStoppedAt()));
        report.set("actualWindows", windows);
        report.put("assignmentCutoffAt", snapshot.assignments().stream().map(OnlineAssignmentEntity::getCreatedAt).filter(Objects::nonNull).max(LocalDateTime::compareTo).map(LocalDateTime::toString).orElse(null));
        report.set("analysisPlan", manifest.path("analysisPlan")); report.set("groups", JsonUtils.parse(JsonUtils.toJson(groupRows), JsonNode.class));
        var difference = node(); String baseline = groupRows.get("BASELINE").path("primaryValidRatio").asText(null);
        String candidate = groupRows.get("CANDIDATE").path("primaryValidRatio").asText(null);
        difference.put("candidateMinusBaselineRatio", baseline == null || candidate == null ? null : new BigDecimal(candidate).subtract(new BigDecimal(baseline)).toPlainString());
        difference.set("mdeAbsoluteRatio", manifest.path("analysisPlan").path("mdeAbsoluteRatio"));
        difference.put("interpretation", "DESCRIPTIVE_NOT_CAUSAL"); report.set("primaryDifference", difference);
        var qualityCoverage = node(); var resourceCoverage = node(); var latencyCoverage = node();
        for (var entry : groupRows.entrySet()) {
            var group = entry.getValue(); long assigned = count(group, "assignedTaskCount"), documents = count(group, "participatingDocumentCount");
            qualityCoverage.put(entry.getKey(), ratio(count(group, "blindedDocumentCount"), documents));
            resourceCoverage.set(entry.getKey(), JsonUtils.parse(JsonUtils.toJson(Map.of("ledgerCoverage", Optional.ofNullable(ratio(count(group, "ledgerAvailableCount"), assigned)).orElse("UNKNOWN"),
                    "currencyCoverage", Optional.ofNullable(ratio(count(group, "currencyAvailableCount"), assigned)).orElse("UNKNOWN"))), JsonNode.class));
            latencyCoverage.set(entry.getKey(), JsonUtils.parse(JsonUtils.toJson(Map.of("queueCoverage", Optional.ofNullable(ratio(count(group, "queueLatencyAvailableCount"), assigned)).orElse("UNKNOWN"),
                    "executionCoverage", Optional.ofNullable(ratio(count(group, "executionLatencyAvailableCount"), assigned)).orElse("UNKNOWN"),
                    "endToEndCoverage", Optional.ofNullable(ratio(count(group, "endToEndLatencyAvailableCount"), assigned)).orElse("UNKNOWN"))), JsonNode.class));
        }
        report.set("qualityCoverage", qualityCoverage); report.set("resourceCoverage", resourceCoverage); report.set("latencyCoverage", latencyCoverage);
        report.set("ruleOutcomes", JsonUtils.parse(JsonUtils.toJson(documentRuleCounts), JsonNode.class));
        report.set("taskRuleOutcomes", JsonUtils.parse(JsonUtils.toJson(ruleCounts), JsonNode.class));
        report.set("approvalFeedback", approvalFeedback(snapshot, assignmentMap));
        report.set("feedbackStrata", JsonUtils.parse(JsonUtils.toJson(strata), JsonNode.class));
        report.set("budgetByCurrency", JsonUtils.parse(JsonUtils.toJson(budgets.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toPlainString()))), JsonNode.class));
        report.set("srmDiagnostics", JsonUtils.parse(JsonUtils.toJson(Map.of("range", rangeSrm, "runtime", runtimeSrm)), JsonNode.class));
        report.set("exceptions", JsonUtils.parse(JsonUtils.toJson(exceptions), JsonNode.class)); report.set("decisionEligibility", eligibility);
        report.set("limitations", JsonUtils.parse(JsonUtils.toJson(List.of("DESCRIPTIVE_NOT_CAUSAL", "FIRST_ACCEPTED_ASSIGNMENT_PER_DOCUMENT",
                "LATEST_ATTEMPT_PER_FROZEN_RULE", "UNKNOWN_IS_NOT_ZERO", "CURRENT_OBSERVATION_NOT_EVENT_TIME_RECONSTRUCTION", "TRACE_NOT_AUTHORITATIVE")), JsonNode.class));
        return report;
    }

    private static String ruleOutcome(OnlineAssignmentEntity assignment, JsonNode rule, OnlineEvaluationAttemptEntity attempt,
            Map<Long, EvaluationResultEntity> results, Map<Long, List<EvaluationMetricEntity>> metrics,
            Map<Long, EvaluationEvidenceReferenceEntity> evidence, Map<Long, List<EvaluationMetricEvidenceEntity>> links) {
        if (attempt == null) { return "MISSING"; }
        var expected = new ArrayList<JsonNode>(); rule.path("expectedBindings").forEach(item -> {
            if (assignment.getDocumentId().toString().equals(item.path("documentId").asText())) { expected.add(item.path("expectedJson")); }
        });
        if (expected.size() != 1 || !Objects.equals(attempt.getExpectedHash(), OnlineProtocolUtils.hash("online.expected", expected.getFirst()))
                || !Objects.equals(attempt.getEvaluatorVersionId(), OnlineProtocolUtils.id(rule.path("evaluatorVersionId").asText()))) { return "MISSING"; }
        var result = results.get(attempt.getResultId());
        if (result == null || !Objects.equals(result.getOnlineAssignmentId(), assignment.getId()) || !Objects.equals(result.getOnlineEvaluationAttemptId(), attempt.getId())
                || !Objects.equals(result.getTaskId(), assignment.getTaskId()) || !Objects.equals(result.getExecutionId(), assignment.getExecutionId())) { return "MISSING"; }
        if ("SKIPPED".equals(result.getStatus())) { return "NOT_APPLICABLE"; }
        if (!Set.of("PASSED", "FAILED").contains(result.getStatus())) { return "MISSING"; }
        var values = metrics.getOrDefault(attempt.getId(), List.of()).stream().filter(item -> rule.path("metricKey").asText().equals(item.getMetricKey())).toList();
        if (values.size() != 1) { return "MISSING"; } var value = values.getFirst();
        if (!Objects.equals(value.getEvaluationResultId(), result.getId()) || !Objects.equals(value.getEvaluatorVersionId(), attempt.getEvaluatorVersionId())
                || !Objects.equals(value.getOnlineAssignmentId(), assignment.getId()) || !Objects.equals(value.getTaskId(), assignment.getTaskId())
                || !Objects.equals(value.getExecutionId(), assignment.getExecutionId()) || !Objects.equals(value.getSpaceId(), assignment.getSpaceId())
                || !"EVALUATOR".equals(value.getSource()) || !Objects.equals(value.getProducerId(), result.getId()) || !Integer.valueOf(1).equals(value.getContractVersion())
                || !rule.path("valueType").asText().equals(value.getValueType()) || !rule.path("unit").asText().equals(value.getUnit())) { return "MISSING"; }
        var references = links.getOrDefault(value.getId(), List.of()); if (references.isEmpty()) { return "MISSING"; }
        for (var link : references) {
            var ref = evidence.get(link.getEvidenceReferenceId());
            if (ref == null || !Objects.equals(ref.getOnlineEvaluationAttemptId(), attempt.getId()) || !Objects.equals(ref.getResultId(), result.getId())
                    || !Objects.equals(ref.getOnlineAssignmentId(), assignment.getId()) || !Objects.equals(ref.getTaskId(), assignment.getTaskId())
                    || !Objects.equals(ref.getExecutionId(), assignment.getExecutionId()) || !Objects.equals(ref.getSpaceId(), assignment.getSpaceId())
                    || ref.getContentHash() == null || !ref.getContentHash().matches("[0-9a-f]{64}")) { return "MISSING"; }
        }
        int comparison;
        switch (value.getValueType()) {
            case "NUMBER" -> { if (value.getNumericValue() == null || !rule.path("threshold").isNumber()) { return "MISSING"; }
                comparison = value.getNumericValue().compareTo(rule.path("threshold").decimalValue()); }
            case "BOOLEAN" -> { if (value.getBooleanValue() == null || !rule.path("threshold").isBoolean()) { return "MISSING"; }
                comparison = Boolean.compare(value.getBooleanValue(), rule.path("threshold").booleanValue()); }
            case "STRING" -> { if (value.getStringValue() == null || !rule.path("threshold").isTextual()) { return "MISSING"; }
                comparison = value.getStringValue().compareTo(rule.path("threshold").textValue()); }
            default -> { return "MISSING"; }
        }
        boolean pass = switch (rule.path("comparison").asText()) { case "EQ" -> comparison == 0; case "GE" -> comparison >= 0; case "LE" -> comparison <= 0; default -> false; };
        return pass ? "PASS" : "FAIL";
    }

    private static EvaluationFeedbackEntity qualityTail(OnlineAssignmentEntity assignment, List<EvaluationFeedbackEntity> rows) {
        if (rows.isEmpty()) { return null; }
        var children = new HashMap<Long, EvaluationFeedbackEntity>(); EvaluationFeedbackEntity root = null;
        for (var row : rows) {
            if (!OnlineQualityFeedbackFacts.valid(row) || !Objects.equals(row.getTaskId(), assignment.getTaskId())
                    || !Objects.equals(row.getExecutionId(), assignment.getExecutionId())) { return null; }
            if (row.getSupersedesFeedbackId() == null) { if (root != null) { return null; } root = row; }
            else if (children.put(row.getSupersedesFeedbackId(), row) != null) { return null; }
        }
        var seen = new HashSet<Long>(); var tail = root;
        while (tail != null && children.containsKey(tail.getId())) {
            if (!seen.add(tail.getId())) { return null; } tail = children.get(tail.getId());
        }
        return tail != null && seen.size() + 1 == rows.size() ? tail : null;
    }

    private static void resource(ObjectNode group, OnlineReportTaskFactVO task, OnlineReportExecutionFactVO execution,
            LocalDateTime cutoff, Map<String, BigDecimal> budgets) {
        if (task != null && task.createdAt() != null && execution != null && execution.finishedAt() != null && !time(execution.finishedAt()).isAfter(cutoff)) {
            long millis = Duration.between(time(task.createdAt()), time(execution.finishedAt())).toMillis();
            if (millis >= 0) { increment(group, "endToEndLatencyAvailableCount"); group.put("endToEndLatencyTotalMillis", count(group, "endToEndLatencyTotalMillis") + millis); }
        }
        if (task != null && task.createdAt() != null && execution != null && execution.startedAt() != null && !time(execution.startedAt()).isAfter(cutoff)) {
            long millis = Duration.between(time(task.createdAt()), time(execution.startedAt())).toMillis();
            if (millis >= 0) { increment(group, "queueLatencyAvailableCount"); group.put("queueLatencyTotalMillis", count(group, "queueLatencyTotalMillis") + millis); }
        }
        if (execution != null && execution.startedAt() != null && execution.finishedAt() != null && !time(execution.finishedAt()).isAfter(cutoff)) {
            long millis = Duration.between(time(execution.startedAt()), time(execution.finishedAt())).toMillis();
            if (millis >= 0) { increment(group, "executionLatencyAvailableCount"); group.put("executionLatencyTotalMillis", count(group, "executionLatencyTotalMillis") + millis); }
        }
        var usage = task == null ? null : task.usage();
        if (usage == null || task.ledgerId() == null || task.ledgerHash() == null || task.ledgerRecordedAt() == null || time(task.ledgerRecordedAt()).isAfter(cutoff)
                || usage.executionId() == null || !usage.executionId().toString().equals(task.executionId()) || execution == null || !Objects.equals(task.executionId(), execution.executionId())
                || usage.inputTokens() == null || usage.outputTokens() == null
                || usage.inputTokens() < 0 || usage.outputTokens() < 0 || !task.ledgerHash().equals(OnlineProtocolUtils.hash("online.report-ledger", usage))) {
            increment(group, "ledgerUnknownCount"); return;
        }
        increment(group, "ledgerAvailableCount");
        if (usage.inputTokensEstimated() == null || usage.outputTokensEstimated() == null) { increment(group, "tokenEstimationUnknownCount"); }
        else if (usage.inputTokensEstimated() || usage.outputTokensEstimated()) { increment(group, "estimatedTokenLedgerCount"); }
        BigInteger total = BigInteger.valueOf(usage.inputTokens()).add(BigInteger.valueOf(usage.outputTokens()));
        group.put("ledgerTokens", new BigInteger(group.path("ledgerTokens").asText("0")).add(total).toString());
        if (usage.currency() == null || !usage.currency().matches("[A-Z]{3}") || usage.inputPricePerMillion() == null || usage.outputPricePerMillion() == null
                || !Integer.valueOf(1).equals(usage.pricingSchemaVersion()) || usage.modelConfigVersion() == null || usage.pricingCapturedAt() == null
                || new BigDecimal(usage.inputPricePerMillion()).signum() < 0 || new BigDecimal(usage.outputPricePerMillion()).signum() < 0) { return; }
        increment(group, "currencyAvailableCount");
        increment(group, "currencyCount_" + usage.currency());
        BigDecimal cost = new BigDecimal(usage.inputPricePerMillion()).multiply(BigDecimal.valueOf(usage.inputTokens()))
                .add(new BigDecimal(usage.outputPricePerMillion()).multiply(BigDecimal.valueOf(usage.outputTokens()))).divide(BigDecimal.valueOf(1000000), 12, RoundingMode.HALF_UP);
        budgets.merge(usage.currency(), cost, BigDecimal::add);
        var currencies = group.has("budgetByCurrency") ? (ObjectNode) group.get("budgetByCurrency") : node();
        currencies.put(usage.currency(), new BigDecimal(currencies.path(usage.currency()).asText("0")).add(cost).toPlainString());
        group.set("budgetByCurrency", currencies);
    }
    /** 每个提案选截止内最新导入事实；审批和编辑幅度不进入原始质量分子。 */
    private static JsonNode approvalFeedback(OnlineReportSnapshotService.Snapshot snapshot, Map<Long, OnlineAssignmentEntity> assignments) {
        var selected = new TreeMap<String, EvaluationFeedbackEntity>();
        for (var row : snapshot.feedback()) {
            if ("CHANGE_REQUEST".equals(row.getSourceType()) && row.getSourceBusinessId() != null) {
                selected.merge(row.getSourceBusinessId(), row, (first, next) -> {
                    if (first.getCreatedAt() != null && next.getCreatedAt() != null && !first.getCreatedAt().equals(next.getCreatedAt())) {
                        return first.getCreatedAt().isAfter(next.getCreatedAt()) ? first : next;
                    }
                    return first.getId() > next.getId() ? first : next;
                });
            }
        }
        var groups = new LinkedHashMap<String, ObjectNode>();
        var recorded = new HashSet<Long>();
        for (String variant : List.of("BASELINE", "CANDIDATE")) { groups.put(variant, node()); }
        for (var row : selected.values()) {
            var assignment = assignments.get(row.getOnlineAssignmentId());
            if (assignment == null || !groups.containsKey(assignment.getVariant())) { continue; }
            var group = groups.get(assignment.getVariant()); increment(group, "selectedRequestCount");
            try {
                var facts = JsonUtils.parseStrict(row.getFactsJson(), JsonNode.class);
                var difference = JsonUtils.parseStrict(facts.path("onlineApprovalDifference").toString(), OnlineApprovalDifferenceVO.class);
                String status = facts.path("status").asText();
                String label = switch (status) { case "APPROVED", "MERGED" -> "ACCEPTED"; case "REJECTED" -> "REJECTED"; case "RETURNED" -> "NEEDS_CHANGES"; default -> "UNKNOWN"; };
                var captured = new ChangeRequestFeedbackSnapshotVO(OnlineProtocolUtils.id(row.getSourceBusinessId()), row.getSpaceId(), row.getTaskId(), row.getExecutionId(),
                        status, facts.path("resolutionType").asText(null), facts.path("reviewCommentHash").asText(null),
                        facts.path("reviewedBy").asText().isBlank() ? null : OnlineProtocolUtils.id(facts.path("reviewedBy").asText()),
                        facts.path("reviewedAt").asText().isBlank() ? null : time(facts.path("reviewedAt").asText()),
                        facts.path("revisionNo").isInt() ? facts.path("revisionNo").intValue() : null, row.getSourceHash(), difference);
                var input = difference.input();
                if (!OnlineApprovalDifferenceUtils.valid(difference) || !row.getSourceHash().equals(OnlineApprovalDifferenceUtils.feedbackHash(captured))
                        || !Objects.equals(row.getLabel(), label) || "UNKNOWN".equals(label)
                        || !Objects.equals(difference.changeRequestId(), row.getSourceBusinessId())
                        || !Objects.equals(row.getOriginalEvidenceId(), captured.changeRequestId()) || !Objects.equals(row.getOriginalEvidenceHash(), difference.originalEvidenceHash())
                        || !Objects.equals(input.assignmentId(), assignment.getId().toString()) || !Objects.equals(input.bindingHash(), assignment.getBindingHash())
                        || !Objects.equals(input.experimentId(), assignment.getExperimentId().toString()) || !Objects.equals(input.documentId(), assignment.getDocumentId().toString())
                        || !Objects.equals(input.agentId(), assignment.getAgentId().toString()) || !Objects.equals(input.spaceId(), assignment.getSpaceId().toString())
                        || !Objects.equals(row.getSpaceId(), assignment.getSpaceId()) || !Objects.equals(row.getTaskId(), assignment.getTaskId())
                        || !Objects.equals(row.getExecutionId(), assignment.getExecutionId()) || !Objects.equals(input.taskId(), row.getTaskId().toString())
                        || !Objects.equals(input.executionId(), row.getExecutionId().toString())) { throw new IllegalArgumentException("审批事实不一致"); }
                recorded.add(assignment.getId()); increment(group, "status_" + status);
                if (OnlineApprovalDifferenceUtils.COMPARABLE.equals(difference.state())) {
                    increment(group, "comparableRequestCount");
                    group.put("addedCharacters", count(group, "addedCharacters") + difference.addedCharacters());
                    group.put("deletedCharacters", count(group, "deletedCharacters") + difference.deletedCharacters());
                    group.put("changedSegments", count(group, "changedSegments") + difference.changedSegments());
                    group.put("modificationRatioSum", new BigDecimal(group.path("modificationRatioSum").asText("0")).add(difference.humanModificationRatio()).toPlainString());
                } else { increment(group, "notComparableRequestCount"); increment(group, "reason_" + difference.reason()); }
            } catch (RuntimeException invalid) { increment(group, "unknownRequestCount"); }
        }
        for (var entry : groups.entrySet()) {
            var group = entry.getValue(); long comparable = count(group, "comparableRequestCount");
            group.put("meanHumanModificationRatio", comparable == 0 ? null : new BigDecimal(group.path("modificationRatioSum").asText()).divide(BigDecimal.valueOf(comparable), 12, RoundingMode.HALF_UP).toPlainString());
            group.put("recordedAssignmentCount", assignments.values().stream().filter(row -> entry.getKey().equals(row.getVariant()) && recorded.contains(row.getId())).count());
            group.put("unrecordedAssignmentCount", assignments.values().stream().filter(row -> entry.getKey().equals(row.getVariant()) && !recorded.contains(row.getId())).count());
            for (String key : List.of("selectedRequestCount", "comparableRequestCount", "notComparableRequestCount", "unknownRequestCount", "status_APPROVED", "status_MERGED", "status_REJECTED", "status_RETURNED")) {
                if (!group.has(key)) { group.put(key, 0); }
            }
        }
        var result = node(); result.set("groups", JsonUtils.parse(JsonUtils.toJson(groups), JsonNode.class));
        result.put("unit", "LATEST_IMPORTED_CHANGE_REQUEST"); result.put("unrecordedMeaning", "PENDING_OR_NOT_APPLICABLE_OR_NOT_IMPORTED");
        return result;
    }
    private static String executionStatus(OnlineReportExecutionFactVO fact, LocalDateTime cutoff) {
        if (fact == null || !"AVAILABLE".equals(fact.state()) || fact.createdAt() != null && time(fact.createdAt()).isAfter(cutoff)) { return "UNKNOWN"; }
        if (TERMINAL.contains(fact.status()) && (fact.finishedAt() == null || time(fact.finishedAt()).isAfter(cutoff))) { return "UNKNOWN"; }
        return fact.status() == null ? "UNKNOWN" : fact.status();
    }
    private static boolean covered(long valid, long total) { return total > 0 && valid * 10000 >= total * MIN_COVERAGE_BPS; }
    private static String ratio(long numerator, long denominator) { return denominator == 0 ? null : BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 12, RoundingMode.HALF_UP).toPlainString(); }
    private static String text(LocalDateTime value) { return value == null ? null : value.toString(); }
    private static LocalDateTime time(String value) { return LocalDateTime.parse(value); }
    private static ObjectNode node() { return (ObjectNode) JsonUtils.parse("{}", JsonNode.class); }
    private static long count(ObjectNode group, String key) { return group.path(key).asLong(); }
    private static void increment(ObjectNode group, String key) { group.put(key, count(group, key) + 1); }
}
