package com.agentdoc.evaluation.service;

import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.agentdoc.evaluation.enums.*;
import com.agentdoc.evaluation.pojo.dto.*;
import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class OnlineExperimentRequestValidatorTest {
    static OnlineExperimentCreateDTO request() {
        var expected = JsonUtils.parseStrict("{\"assertions\":[{\"field\":\"originalText\",\"operator\":\"CONTAINS\",\"value\":\"规范\"}]}", JsonNode.class);
        return new OnlineExperimentCreateDTO("review-1", "101", "201", " 质量实验 ", List.of("302", "301"),
                "候选提示词", 5000, "10000", 10, "1000", null, null,
                new OnlineAnalysisPlanDTO(OnlineAnalysisMode.ENGINEERING, "0.10", "工程观察", true),
                List.of(new OnlineRuleBindingDTO("quality", OnlineRuleRole.PRIMARY, "401",
                        "evaluation.online-original-text.pass-ratio", EvaluationMetricValueType.NUMBER, "ratio",
                        OnlineComparison.EQ, JsonUtils.parseStrict("\"1.0\"", JsonNode.class),
                        OnlineEvidenceTarget.ORIGINAL_TEXT,
                        List.of(new OnlineRuleExpectedDTO("302", expected), new OnlineRuleExpectedDTO("301", expected)))));
    }

    @Test
    void normalizesDocumentOrderDefaultsAndDecimalTextWithoutChangingAssertions() {
        var value = OnlineExperimentRequestValidator.parse(JsonUtils.toJson(request()));
        assertThat(value.documentIds()).containsExactly("301", "302");
        assertThat(value.rules().getFirst().expectedBindings()).extracting(OnlineRuleExpectedDTO::documentId).containsExactly("301", "302");
        assertThat(value.name()).isEqualTo("质量实验");
        assertThat(value.assignmentWindowSeconds()).isEqualTo(604800);
        assertThat(value.analysisPlan().mdeAbsoluteRatio()).isEqualTo("0.1");
        assertThat(value.rules().getFirst().threshold().asText()).isEqualTo("1");
    }

    @Test
    void rejectsDuplicateKeyNumericIdFloatWeightAndUnknownField() {
        String json = JsonUtils.toJson(request());
        for (String invalid : List.of(json.replace("\"spaceId\":\"101\"", "\"spaceId\":101"),
                json.replace("\"candidateWeightBps\":5000", "\"candidateWeightBps\":5000.0"),
                json.replace("\"clientRequestKey\":\"review-1\"", "\"clientRequestKey\":\"review-1\",\"clientRequestKey\":\"evil\""),
                json.replace("\"spaceId\":\"101\"", "\"spaceId\":\"101\",\"seed\":\"chosen\""))) {
            assertThatThrownBy(() -> OnlineExperimentRequestValidator.parse(invalid)).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void rejectsDuplicateScopeIncompleteExpectedAndOversizedInteger() {
        String json = JsonUtils.toJson(request());
        for (String invalid : List.of(json.replace("\"302\",\"301\"", "\"301\",\"301\""),
                json.replace("\"documentId\":\"302\"", "\"documentId\":\"301\""),
                json.replace("\"authorizedTokenBudget\":\"10000\"", "\"authorizedTokenBudget\":\"9223372036854775808\""))) {
            assertThatThrownBy(() -> OnlineExperimentRequestValidator.parse(invalid)).isInstanceOf(BusinessException.class);
        }
    }
}
