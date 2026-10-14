package com.agentdoc.evaluation.evaluator;

import com.agentdoc.common.feign.vo.OnlineOriginalChangesVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineOriginalChangeUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.enums.EvaluationEvidenceType;
import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import com.agentdoc.evaluation.metric.EvidenceReferenceValue;
import com.agentdoc.evaluation.metric.StandardMetricOutput;
import com.agentdoc.evaluation.metric.StandardMetricValue;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.agentdoc.evaluation.enums.EvaluationMetricDirection.HIGHER_IS_BETTER;
import static com.agentdoc.evaluation.enums.EvaluationMetricSource.EVALUATOR;
import static com.agentdoc.common.enums.OnlineReasonCode.*;
import static com.agentdoc.common.utils.OnlineOriginalChangeUtils.*;

/** 对完整 LIVE 原始集合求值，不选择最优提案，不把缺失证据变成零分。 */
@Component
public class OnlineOriginalChangeEvaluator {
    public static final String IMPLEMENTATION_VERSION = "online-original-change-v1";
    public static final String VALID_METRIC = "evaluation.online-document-change.valid";
    public static final String ACCURACY_METRIC = "evaluation.online-document-change.accuracy";
    private static final int METRIC_SCALE = 10;

    public DeterministicEvaluationOutcome evaluate(String config, String expected, OnlineOriginalChangesVO original) {
        OnlineRuleContractValidator.validate(OnlineRuleContractValidator.CHANGE, config, false);
        OnlineRuleContractValidator.validate(OnlineRuleContractValidator.CHANGE, expected, true);
        if (original == null || UNAVAILABLE.equals(original.state())) { return missing(UNAVAILABLE); }
        if (original.schemaVersion() != SCHEMA_VERSION || !AVAILABLE.equals(original.state()) || original.input() == null
                || original.items() == null || original.items().isEmpty()
                || original.items().stream().anyMatch(item -> !OnlineOriginalChangeUtils.valid(original.input(), item))
                || original.items().stream().map(item -> item.outputType() + ":" + item.evidenceId()).distinct().count() != original.items().size()) {
            return new DeterministicEvaluationOutcome(EvaluationResultStatus.ERROR, ORIGINAL_EVIDENCE_INVALID.name(), "{}", List.of(), List.of());
        }
        if (!original.input().completed()) { return missing(UNAVAILABLE); }
        var expectation = OnlineProtocolUtils.object(expected);
        String type = expectation.path("requiredOutputType").textValue();
        if (original.items().stream().anyMatch(item -> !type.equals(item.outputType()))) { return missing(ORIGINAL_CHANGE_NOT_APPLICABLE.name()); }
        boolean valid = original.items().stream().allMatch(item -> item.structureValid()
                && Objects.equals(original.input().baseVersion(), item.baseVersion())
                && (PROPOSAL.equals(type) ? item.outputVersion() == null
                : original.items().size() == 1 && Objects.equals(item.outputVersion(), Math.addExact(item.baseVersion(), 1L))));
        var references = new ArrayList<EvidenceReferenceValue>();
        for (var item : original.items()) {
            String key = "original-change-" + item.outputType() + "-" + item.evidenceId();
            references.add(new EvidenceReferenceValue(key, EvaluationEvidenceType.ORIGINAL_CHANGE, item.evidenceId(), item.payloadHash(),
                    "线上原始变更", JsonUtils.toJson(Map.of("taskId", original.input().taskId(), "executionId", original.input().executionId(),
                    "outputType", item.outputType(), "identityHash", item.identityHash(), "capturedAt", item.capturedAt()))));
        }
        var keys = references.stream().map(EvidenceReferenceValue::referenceKey).toList();
        var metrics = new ArrayList<StandardMetricOutput>();
        metrics.add(new StandardMetricOutput(StandardMetricValue.bool(VALID_METRIC, valid, HIGHER_IS_BETTER, EVALUATOR), keys));
        boolean passed = valid;
        if (expectation.has("targetContentSha256") && original.items().stream().allMatch(item -> item.contentHash() != null)) {
            String target = expectation.path("targetContentSha256").textValue();
            long matches = original.items().stream().filter(item -> target.equals(item.contentHash())).count();
            var accuracy = BigDecimal.valueOf(matches).divide(BigDecimal.valueOf(original.items().size()), METRIC_SCALE, RoundingMode.HALF_UP);
            metrics.add(new StandardMetricOutput(StandardMetricValue.number(ACCURACY_METRIC, accuracy, "ratio", HIGHER_IS_BETTER, EVALUATOR), keys));
            passed &= matches == original.items().size();
        } else if (expectation.has("targetContentSha256")) { passed = false; }
        return new DeterministicEvaluationOutcome(passed ? EvaluationResultStatus.PASSED : EvaluationResultStatus.FAILED,
                passed ? ORIGINAL_CHANGE_VALID.name() : ORIGINAL_CHANGE_FAILED.name(), JsonUtils.toJson(Map.of("originalItemCount", original.items().size())),
                List.copyOf(metrics), List.copyOf(references));
    }
    private static DeterministicEvaluationOutcome missing(String reason) {
        return new DeterministicEvaluationOutcome(EvaluationResultStatus.SKIPPED, reason, "{}", List.of(), List.of());
    }
}
