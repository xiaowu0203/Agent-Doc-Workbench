package com.agentdoc.evaluation.evaluator;

import com.agentdoc.common.feign.vo.AgentOnlineOriginalTextVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineOriginalTextUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.enums.EvaluationEvidenceType;
import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import com.agentdoc.evaluation.metric.EvidenceReferenceValue;
import com.agentdoc.evaluation.metric.StandardMetricOutput;
import com.agentdoc.evaluation.metric.StandardMetricValue;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import static com.agentdoc.evaluation.enums.EvaluationMetricDirection.HIGHER_IS_BETTER;
import static com.agentdoc.evaluation.enums.EvaluationMetricSource.EVALUATOR;
import static com.agentdoc.common.enums.OnlineReasonCode.*;

/** 只读取不可变原始文本，保留有效零分；规则失败不等于执行失败。 */
@Component
public class OnlineOriginalTextEvaluator {
    public static final String IMPLEMENTATION_VERSION = "online-original-text-v1";
    public static final String METRIC_KEY = "evaluation.online-original-text.pass-ratio";
    private static final String REFERENCE = "original-text";
    private static final int METRIC_SCALE = 10;

    public DeterministicEvaluationOutcome evaluate(String config, String expected, AgentOnlineOriginalTextVO original) {
        OnlineRuleContractValidator.validate(OnlineRuleContractValidator.TEXT, config, false);
        OnlineRuleContractValidator.validate(OnlineRuleContractValidator.TEXT, expected, true);
        if (original == null || OnlineOriginalTextUtils.UNAVAILABLE.equals(original.state())) {
            return new DeterministicEvaluationOutcome(EvaluationResultStatus.SKIPPED, OnlineOriginalTextUtils.UNAVAILABLE,
                    "{}", List.of(), List.of());
        }
        if (!OnlineOriginalTextUtils.valid(original)) {
            return new DeterministicEvaluationOutcome(EvaluationResultStatus.ERROR, ORIGINAL_EVIDENCE_INVALID.name(),
                    "{}", List.of(), List.of());
        }
        var assertions = OnlineProtocolUtils.object(expected).path("assertions");
        int passed = 0;
        for (var assertion : assertions) {
            String value = assertion.path("value").textValue();
            boolean matches = switch (assertion.path("operator").textValue()) {
                case "CONTAINS" -> original.originalText().contains(value);
                case "NOT_CONTAINS" -> !original.originalText().contains(value);
                case "EXACT" -> original.originalText().equals(value);
                case "SHA256" -> original.contentHash().equals(value);
                default -> throw new IllegalStateException("已校验断言操作符不匹配");
            };
            if (matches) { passed++; }
        }
        var ratio = BigDecimal.valueOf(passed).divide(BigDecimal.valueOf(assertions.size()), METRIC_SCALE, RoundingMode.HALF_UP);
        var evidence = new EvidenceReferenceValue(REFERENCE, EvaluationEvidenceType.ORIGINAL_TEXT, original.evidenceId(),
                original.contentHash(), "线上原始最终文本", JsonUtils.toJson(Map.of("taskId", original.taskId(),
                "executionId", original.executionId(), "identityHash", original.identityHash(), "capturedAt", original.capturedAt())));
        return new DeterministicEvaluationOutcome(passed == assertions.size() ? EvaluationResultStatus.PASSED : EvaluationResultStatus.FAILED,
                passed == assertions.size() ? ORIGINAL_TEXT_ASSERTIONS_PASSED.name() : ORIGINAL_TEXT_ASSERTIONS_FAILED.name(),
                JsonUtils.toJson(Map.of("assertionCount", assertions.size(), "passedAssertionCount", passed)),
                List.of(new StandardMetricOutput(StandardMetricValue.number(METRIC_KEY, ratio, "ratio", HIGHER_IS_BETTER, EVALUATOR),
                        List.of(REFERENCE))), List.of(evidence));
    }
}
