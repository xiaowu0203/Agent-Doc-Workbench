package com.agentdoc.evaluation.service;

import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.feign.vo.OnlineReviewGoalVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.evaluation.enums.EvaluationFeedbackLabel;
import com.agentdoc.evaluation.mapper.EvaluationFeedbackMapper;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.dto.EvaluationFeedbackCreateDTO;
import com.agentdoc.evaluation.pojo.dto.OnlineQualityFeedbackDTO;
import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.param.OnlineQualityHistorySearchParam;
import com.agentdoc.evaluation.pojo.vo.OnlineBlindReviewContextVO;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnlineQualityHistoryServiceTest {
    private final OnlineBlindReviewService reviews = mock(OnlineBlindReviewService.class);
    private final EvaluationFeedbackMapper feedback = mock(EvaluationFeedbackMapper.class);
    private final OnlineAssignmentMapper assignments = mock(OnlineAssignmentMapper.class);
    private final OnlineExperimentMapper experiments = mock(OnlineExperimentMapper.class);
    private final List<EvaluationFeedbackEntity> stored = new ArrayList<>();
    private final OnlineOriginalInputVO input = new OnlineOriginalInputVO("61", "71", "10", "20", "11", "51", "a".repeat(64), "41", "FORMAL", 3L, "b".repeat(64), true);
    private OnlineQualityFeedbackPersistenceService writer;
    private final OnlineQualityHistoryService history = new OnlineQualityHistoryService(reviews, feedback);
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "RS256")
                .subject("501").claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER).build()));
        var assignment = new OnlineAssignmentEntity(); assignment.setId(51L); assignment.setTaskId(61L); assignment.setExecutionId(71L);
        assignment.setExperimentId(11L); assignment.setSpaceId(10L); assignment.setAgentId(20L); assignment.setDocumentId(41L);
        assignment.setBindingSchemaVersion(2); assignment.setBindingHash(input.bindingHash());
        var experiment = new OnlineExperimentEntity(); experiment.setId(11L); experiment.setSpaceId(10L);
        when(assignments.lockTask(61L)).thenReturn(assignment); when(experiments.lock(11L)).thenReturn(experiment);
        when(feedback.selectList(any())).thenAnswer(call -> List.copyOf(stored));
        doAnswer(call -> { stored.add(call.getArgument(0)); return 1; }).when(feedback).insert(any(EvaluationFeedbackEntity.class));
        writer = new OnlineQualityFeedbackPersistenceService(assignments, feedback, experiments, mock(OnlineReviewExposureService.class), mock(OnlineBlindReviewReceiptService.class));
        var original = JsonUtils.parse(JsonUtils.toJson(Map.of("taskId", "61", "executionId", "71", "documentId", "41", "baseVersion", 3,
                "inputHash", input.inputHash(), "originalEvidenceId", "71", "originalEvidenceHash", "c".repeat(64))), JsonNode.class);
        when(reviews.read("11", "51", "TEXT")).thenReturn(new OnlineBlindReviewContextVO("51", "UNKNOWN", false,
                new OnlineReviewGoalVO("61", "d".repeat(64), "goal"), original));
        when(reviews.spaceId("11", "51")).thenReturn("10");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private OnlineQualityHistorySearchParam query() { var query = new OnlineQualityHistorySearchParam(); query.setTarget("TEXT"); query.setPageSize(1); return query; }
    private void append(Long actor, String comment) {
        var quality = new OnlineQualityFeedbackDTO("51", "ORIGINAL_TEXT", "71", "c".repeat(64), 0, "NON_BLINDED",
                stored.isEmpty() ? null : stored.getLast().getId().toString(), List.of("RECHECK"), false, null);
        writer.append(input, "d".repeat(64), new EvaluationFeedbackCreateDTO(10L, null, 61L, 71L,
                EvaluationFeedbackLabel.NEEDS_CHANGES, quality.normalizedScore(), comment, quality), actor);
    }
    @Test void validHistoryKeepsZeroAndTailWhileHidingOtherReviewersFreeText() {
        append(502L, "other reviewer mentions CANDIDATE"); append(501L, "my correction");
        var result = history.search("11", "51", query());
        assertThat(result.currentTailFeedbackId()).isEqualTo(stored.getLast().getId().toString());
        assertThat(result.history().total()).isEqualTo(2);
        assertThat(result.history().records().getFirst().rubricScore()).isZero();
        assertThat(result.history().records().getFirst().comment()).isNull();
        assertThat(result.history().records().getFirst().reasonCodes()).isEmpty();
        assertThat(result.lastOwnFeedback().comment()).isEqualTo("my correction");
        assertThat(JsonUtils.toJson(result)).doesNotContain("CANDIDATE", "factsJson", "bindingHash", "nonPromptHash", "variant");
    }
    @Test void emptyHistoryIsConfirmedOnlyAfterAuthorizedOriginalRead() {
        var result = history.search("11", "51", query());
        assertThat(result.currentTailFeedbackId()).isNull(); assertThat(result.history().total()).isZero();
        verify(reviews).read("11", "51", "TEXT");
        when(reviews.read("11", "51", "TEXT")).thenThrow(new BusinessException(403, "source denied"));
        clearInvocations(feedback);
        assertThatThrownBy(() -> history.search("11", "51", query())).hasMessageContaining("denied");
        verifyNoInteractions(feedback);
    }
    @Test void changedFeedbackDigestFailsClosedAndDoesNotProduceAnEmptyChain() {
        append(501L, "original reason"); stored.getFirst().setComment("tampered reason");
        assertThatThrownBy(() -> history.search("11", "51", query())).hasMessageContaining("不可核验");
    }
    @Test void topologyUsesPredecessorsInsteadOfLargestIdAndRejectsDisconnectedRows() {
        var root = new EvaluationFeedbackEntity(); root.setId(100L);
        var tail = new EvaluationFeedbackEntity(); tail.setId(90L); tail.setSupersedesFeedbackId(100L);
        assertThat(OnlineQualityFeedbackFacts.orderedChain(List.of(tail, root))).containsExactly(root, tail);
        tail.setSupersedesFeedbackId(99L);
        assertThat(OnlineQualityFeedbackFacts.orderedChain(List.of(root, tail))).isNull();
        tail.setSupersedesFeedbackId(90L);
        assertThat(OnlineQualityFeedbackFacts.orderedChain(List.of(root, tail))).isNull();
    }
}
