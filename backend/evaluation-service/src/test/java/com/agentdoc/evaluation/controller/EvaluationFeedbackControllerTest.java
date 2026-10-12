package com.agentdoc.evaluation.controller;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.pojo.dto.EvaluationFeedbackCreateDTO;
import com.agentdoc.evaluation.service.EvaluationFeedbackService;
import com.agentdoc.evaluation.service.OnlineQualityFeedbackService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EvaluationFeedbackControllerTest {
    private static final String QUALITY = "\"onlineQuality\":{\"assignmentId\":\"51\",\"originalEvidenceType\":\"ORIGINAL_TEXT\",\"originalEvidenceId\":\"71\",\"originalEvidenceHash\":\"" + "a".repeat(64)
            + "\",\"rubricScore\":0,\"blindness\":\"UNKNOWN\",\"reasonCodes\":[],\"seriousSafetyEvent\":false}";
    private static String body(String quality) { return "{\"spaceId\":10,\"label\":\"REJECTED\"," + quality + "}"; }
    @Test void oldDecimalScoreRetainsLegacyRouteAndNewZeroUsesQualityRoute() throws Exception {
        var legacy = mock(EvaluationFeedbackService.class); var online = mock(OnlineQualityFeedbackService.class);
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var mvc = MockMvcBuilders.standaloneSetup(new EvaluationFeedbackController(legacy, online, validators.getValidator())).build();
            mvc.perform(post("/api/evaluation/feedback").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"spaceId\":10,\"taskId\":61,\"label\":\"ACCEPTED\",\"score\":0.85,\"legacyExtra\":true}")).andExpect(status().isOk());
            var old = org.mockito.ArgumentCaptor.forClass(EvaluationFeedbackCreateDTO.class); verify(legacy).create(old.capture());
            assertThat(old.getValue().score()).isEqualByComparingTo("0.85"); assertThat(old.getValue().onlineQuality()).isNull();
            mvc.perform(post("/api/evaluation/feedback").contentType(MediaType.APPLICATION_JSON).content(body(QUALITY))).andExpect(status().isOk());
            var added = org.mockito.ArgumentCaptor.forClass(EvaluationFeedbackCreateDTO.class); verify(online).create(added.capture());
            assertThat(added.getValue().onlineQuality().rubricScore()).isZero();
        }
    }
    @ParameterizedTest
    @ValueSource(strings = {"\"rubricScore\":0.9", "\"rubricScore\":\"0\"", "\"rubricScore\":5", "\"rubricScore\":-1",
            "\"rubricScore\":999999999999", "\"rubricScore\":0,\"rubricScore\":4"})
    void refusesFractionalCoercedOutOfRangeOrDuplicateRubric(String field) {
        assertThatThrownBy(() -> EvaluationFeedbackCreateDTO.parse(body(QUALITY.replace("\"rubricScore\":0", field)))).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest
    @ValueSource(strings = {"\"assignmentId\":51", "\"assignmentId\":\"051\"", "\"assignmentId\":\"9223372036854775808\""})
    void refusesNonCanonicalIds(String field) {
        assertThatThrownBy(() -> EvaluationFeedbackCreateDTO.parse(body(QUALITY.replace("\"assignmentId\":\"51\"", field)))).isInstanceOf(BusinessException.class);
    }
    @Test void refusesDuplicateNullExtensionUnknownFieldsAndTrailingInput() {
        for (String json : new String[]{body(QUALITY) + "{}", body(QUALITY + ",\"onlineQuality\":null"),
                body(QUALITY + ",\"extra\":true"), body(QUALITY.replace("\"reasonCodes\":[]", "\"reasonCodes\":[],\"extra\":true")),
                body(QUALITY).replace("\"spaceId\":10", "\"spaceId\":10.9")}) {
            assertThatThrownBy(() -> EvaluationFeedbackCreateDTO.parse(json)).isInstanceOf(BusinessException.class);
        }
    }
    @Test void refusesCoercedSafetyDuplicateReasonsAndUnsupportedEvidence() {
        for (String quality : new String[]{QUALITY.replace("\"seriousSafetyEvent\":false", "\"seriousSafetyEvent\":\"false\""),
                QUALITY.replace("\"seriousSafetyEvent\":false", "\"seriousSafetyEvent\":true"),
                QUALITY.replace("\"reasonCodes\":[]", "\"reasonCodes\":[\"FIX\",\"FIX\"]"), QUALITY.replace("ORIGINAL_TEXT", "SUMMARY")}) {
            assertThatThrownBy(() -> EvaluationFeedbackCreateDTO.parse(body(quality))).isInstanceOf(BusinessException.class);
        }
    }
    @Test void retainsExistingBeanValidationForBothRoutes() {
        var legacy = mock(EvaluationFeedbackService.class); var online = mock(OnlineQualityFeedbackService.class);
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var controller = new EvaluationFeedbackController(legacy, online, validators.getValidator());
            assertThatThrownBy(() -> controller.create("{\"label\":\"ACCEPTED\"}")).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> controller.create("{\"spaceId\":10,\"label\":\"ACCEPTED\",\"score\":1.1}")).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> controller.create(body(QUALITY).replace("\"spaceId\":10,", ""))).isInstanceOf(BusinessException.class);
            verifyNoInteractions(legacy, online);
        }
    }
}
