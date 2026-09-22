package com.agentdoc.evaluation.evaluator;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.EvaluationArtifactEvidenceVO;
import com.agentdoc.common.feign.vo.EvaluationEvidenceBundleVO;
import com.agentdoc.common.feign.vo.EvaluationDocumentChangeEvidenceVO;
import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeterministicEvaluatorEngineTest {
    private final DeterministicEvaluatorEngine engine = new DeterministicEvaluatorEngine();

    @Test
    void evaluatesAllBuiltInRulesWithStableMetricContracts() {
        EvaluationEvidenceBundleVO evidence = evidence();

        assertPassed("task-terminal-status", "{}", evidence, "evaluation.task-terminal.success");
        assertPassed("artifact-contract", "{\"requiredTypes\":[\"CHANGE_PROPOSAL\"]}", evidence,
                "evaluation.artifact-contract.valid");
        assertPassed("text-assertion",
                "{\"assertions\":[{\"field\":\"resultSummary\",\"operator\":\"CONTAINS\",\"value\":\"done\"}]}",
                evidence, "evaluation.text-assertion.pass-ratio");
        var documentOutcome = engine.evaluate("document-change-validator", "{}", "{}", evidence,
                List.of(new EvaluationDocumentChangeEvidenceVO(11L, 31L, "a".repeat(64),
                        true, false, "b".repeat(64), null)));
        assertThat(documentOutcome.status()).isEqualTo(EvaluationResultStatus.PASSED);
        assertThat(documentOutcome.metrics()).extracting(value -> value.value().metricKey())
                .containsExactly("evaluation.document-change.valid", "evaluation.document-change.accuracy");
        assertPassed("isolation-invariant", "{}", evidence,
                "evaluation.isolation-invariant.success");
        assertPassed("audit-ledger-integrity", "{}", evidence,
                "evaluation.audit-ledger-integrity.success");
    }

    @Test
    void caseExpectedOverridesVersionConfigurationDeterministically() {
        EvaluationEvidenceBundleVO evidence = evidence();
        String config = "{\"minCount\":2,\"requiredTypes\":[\"OTHER\"]}";
        String expected = "{\"minCount\":1,\"requiredTypes\":[\"CHANGE_PROPOSAL\"]}";

        var first = engine.evaluate("artifact-contract", config, expected, evidence);
        var second = engine.evaluate("artifact-contract", config, expected, evidence);

        assertThat(first).isEqualTo(second);
        assertThat(first.status()).isEqualTo(EvaluationResultStatus.PASSED);
    }

    @Test
    void invalidAssertionProducesEvaluatorContractError() {
        assertThatThrownBy(() -> engine.evaluate("text-assertion",
                "{\"assertions\":[{\"field\":\"resultSummary\",\"operator\":\"REGEX\",\"value\":\".*\"}]}",
                evidence())).isInstanceOf(BusinessException.class);
    }

    private void assertPassed(String key, String expected, EvaluationEvidenceBundleVO evidence,
                              String... metricKeys) {
        var outcome = engine.evaluate(key, expected, evidence);
        assertThat(outcome.status()).isEqualTo(EvaluationResultStatus.PASSED);
        assertThat(outcome.metrics()).extracting(value -> value.value().metricKey())
                .containsExactly(metricKeys);
        assertThat(outcome.metrics()).allSatisfy(metric -> assertThat(metric.evidenceReferenceKeys()).isNotEmpty());
    }

    private EvaluationEvidenceBundleVO evidence() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 17, 12, 0);
        return new EvaluationEvidenceBundleVO(11L, 9L, 2, "COMPLETED", "done", 21L,
                "COMPLETED", "trace", "span", start, start.plusSeconds(2), 10L, 2L, 5L,
                new BigDecimal("0.12"), "CNY", 0, 1, 0, 0, 0,
                List.of(new EvaluationArtifactEvidenceVO(31L, 1, "CHANGE_PROPOSAL", 1,
                        "a".repeat(64), 41L)));
    }
}
