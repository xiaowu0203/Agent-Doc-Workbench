package com.agentdoc.evaluation.evaluator;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.EvaluationEvidenceBundleVO;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 注册新原始产物契约，不扩大旧摘要规则或冒充 LIVE 引擎。 */
class OnlineRuleContractValidatorTest {
    private final EvaluatorContractValidator validator = new EvaluatorContractValidator();
    @Test
    void newTextContractReadsOriginalTextWhileOldRuleStillReadsSummaryOnly() {
        String expected = "{\"assertions\":[{\"field\":\"originalText\",\"operator\":\"CONTAINS\",\"value\":\"规范\"}]}";
        assertThatCode(() -> validator.validateVersion(OnlineRuleContractValidator.TEXT, 1, "{}", 1)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateExpected(OnlineRuleContractValidator.TEXT, expected)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateExpected("text-assertion", expected)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validateVersion(OnlineRuleContractValidator.TEXT, 1, "{\"field\":\"resultSummary\"}", 1))
                .isInstanceOf(BusinessException.class);
    }
    @Test
    void changeContractHasExplicitOutputTypeAndOriginalEvidenceHash() {
        assertThatCode(() -> validator.validateExpected(OnlineRuleContractValidator.CHANGE,
                "{\"requiredOutputType\":\"CHANGE_REQUEST\",\"targetContentSha256\":\"" + "a".repeat(64) + "\"}"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateExpected(OnlineRuleContractValidator.CHANGE,
                "{\"requiredArtifactType\":\"ISOLATED_ARTIFACT\"}")).isInstanceOf(BusinessException.class);
    }
    @Test
    void contractMetadataCannotExecuteThroughOldOfflineEngine() {
        assertThatThrownBy(() -> new DeterministicEvaluatorEngine().evaluate(OnlineRuleContractValidator.TEXT, "{}",
                mock(EvaluationEvidenceBundleVO.class))).isInstanceOf(BusinessException.class);
    }
}
