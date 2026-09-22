package com.agentdoc.evaluation.evaluator;

import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluatorContractValidatorTest {
    private final EvaluatorContractValidator validator = new EvaluatorContractValidator();

    @Test
    void acceptsDeclaredVersionAndCaseContracts() {
        assertThatCode(() -> validator.validateVersion("artifact-contract", 1,
                "{\"minCount\":1,\"maxCount\":2}", 1)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateExpected("text-assertion",
                "{\"assertions\":[{\"field\":\"resultSummary\",\"operator\":\"EXACT\",\"value\":\"ok\"}]}"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownFieldsAndSchemaDrift() {
        assertThatThrownBy(() -> validator.validateVersion("task-terminal-status", 1,
                "{\"dynamicMetricKey\":\"x\"}", 1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validateVersion("task-terminal-status", 2, "{}", 1))
                .isInstanceOf(BusinessException.class);
    }
}
