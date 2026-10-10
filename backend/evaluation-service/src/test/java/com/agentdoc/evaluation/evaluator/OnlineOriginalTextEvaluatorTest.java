package com.agentdoc.evaluation.evaluator;

import com.agentdoc.common.feign.vo.AgentOnlineOriginalTextVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineOriginalTextUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class OnlineOriginalTextEvaluatorTest {
    private final OnlineOriginalTextEvaluator engine = new OnlineOriginalTextEvaluator();
    private AgentOnlineOriginalTextVO original(String text) {
        var value = new AgentOnlineOriginalTextVO(2, "AVAILABLE", "71", "61", "71", "10", "20", "11", "51",
                "a".repeat(64), StableSnapshotUtils.sha256Utf8(text), null, "2026-10-10T20:00:00.123", text);
        return new AgentOnlineOriginalTextVO(2, value.state(), value.evidenceId(), value.taskId(), value.executionId(), value.spaceId(),
                value.agentId(), value.experimentId(), value.assignmentId(), value.bindingHash(), value.contentHash(),
                OnlineOriginalTextUtils.identityHash(value), value.capturedAt(), text);
    }
    private String expected(String operator, String value) {
        return JsonUtils.toJson(Map.of("assertions", List.of(Map.of("field", "originalText", "operator", operator, "value", value))));
    }
    @Test void finalTextSupportsUnicodeExactAndHashWithoutWhitespaceNormalization() {
        String text = " 原始\r\n输出😀 ";
        assertThat(engine.evaluate("{}", expected("EXACT", text), original(text)).status().name()).isEqualTo("PASSED");
        assertThat(engine.evaluate("{}", expected("SHA256", StableSnapshotUtils.sha256Utf8(text)), original(text)).status().name()).isEqualTo("PASSED");
        assertThat(engine.evaluate("{}", expected("EXACT", text.trim()), original(text)).status().name()).isEqualTo("FAILED");
    }
    @Test void validFailureKeepsZeroMetricAndEvidenceWithoutCopyingBody() {
        var output = engine.evaluate("{}", expected("CONTAINS", "期待内容"), original("不满足且保密的原始输出"));
        assertThat(output.status().name()).isEqualTo("FAILED"); assertThat(output.metrics().getFirst().value().numericValue()).isZero();
        assertThat(output.evidence()).hasSize(1); assertThat(output.detailsJson()).doesNotContain("期待内容", "保密");
        assertThat(output.evidence().getFirst().locatorJson()).doesNotContain("保密");
    }
    @Test void absentOriginalIsMissingAndNeverSyntheticZero() {
        var result = engine.evaluate("{}", expected("EXACT", "x"), null);
        assertThat(result.status().name()).isEqualTo("SKIPPED"); assertThat(result.summaryCode()).isEqualTo("EVIDENCE_UNAVAILABLE");
        assertThat(result.metrics()).isEmpty(); assertThat(result.evidence()).isEmpty();
    }
    @Test void bodyOrIdentityTamperIsErrorWithoutQualityMetric() {
        var value = original("真实原始文本");
        var tampered = new AgentOnlineOriginalTextVO(2, value.state(), value.evidenceId(), value.taskId(), value.executionId(), value.spaceId(),
                value.agentId(), value.experimentId(), value.assignmentId(), value.bindingHash(), value.contentHash(), value.identityHash(), value.capturedAt(), "伪造正文");
        assertThat(engine.evaluate("{}", expected("EXACT", "伪造正文"), tampered).metrics()).isEmpty();
        assertThat(engine.evaluate("{}", expected("EXACT", "伪造正文"), tampered).status().name()).isEqualTo("ERROR");
    }
    @Test void partialPassUsesDatabaseCompatibleRatioAndAllRequiredAssertions() {
        var expected = JsonUtils.toJson(Map.of("assertions", List.of(Map.of("field", "originalText", "operator", "CONTAINS", "value", "a"),
                Map.of("field", "originalText", "operator", "NOT_CONTAINS", "value", "b"),
                Map.of("field", "originalText", "operator", "EXACT", "value", "c"))));
        var result = engine.evaluate("{}", expected, original("a"));
        assertThat(result.metrics().getFirst().value().numericValue()).isEqualByComparingTo("0.6666666667");
        assertThat(result.status().name()).isEqualTo("FAILED");
    }
    @Test void invalidFrozenRuleDoesNotBecomeQualityFailure() {
        assertThatThrownBy(() -> engine.evaluate("{}", expected("REGEX", "x"), original("x"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> engine.evaluate("{\"extra\":true}", expected("EXACT", "x"), original("x"))).isInstanceOf(BusinessException.class);
    }
}
