package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.ChangeRequestFeedbackSnapshotVO;
import com.agentdoc.evaluation.enums.EvaluationFeedbackLabel;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationFeedbackMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.pojo.dto.EvaluationFeedbackCreateDTO;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationFeedbackServiceTest {
    @Mock private EvaluationFeedbackMapper feedbackMapper;
    @Mock private EvaluationCaseRunMapper caseRunMapper;
    @Mock private EvaluationCaseAttemptMapper attemptMapper;
    @Mock private EvaluationEvidenceReferenceMapper evidenceMapper;
    @Mock private EvaluationResultMapper resultMapper;
    @Mock private SpaceAccessService spaceAccessService;
    @Mock private TaskFeign taskFeign;

    private EvaluationFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationFeedbackService(feedbackMapper, caseRunMapper, attemptMapper, evidenceMapper,
                resultMapper, spaceAccessService, taskFeign);
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("501")
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsImmutableManualFeedbackForResolvedCaseRun() {
        EvaluationCaseRunEntity caseRun = new EvaluationCaseRunEntity();
        caseRun.setId(21L); caseRun.setRunId(11L); caseRun.setSpaceId(9L); caseRun.setCurrentAttemptId(31L);
        EvaluationCaseAttemptEntity attempt = new EvaluationCaseAttemptEntity();
        attempt.setId(31L); attempt.setRunId(11L); attempt.setCaseRunId(21L); attempt.setReplayTaskId(41L);
        when(caseRunMapper.selectById(21L)).thenReturn(caseRun);
        when(attemptMapper.selectById(31L)).thenReturn(attempt);

        var result = service.create(new EvaluationFeedbackCreateDTO(9L, 21L, null, null,
                EvaluationFeedbackLabel.ACCEPTED, null, " looks good "));

        ArgumentCaptor<EvaluationFeedbackEntity> captor = ArgumentCaptor.forClass(EvaluationFeedbackEntity.class);
        verify(feedbackMapper).insert(captor.capture());
        assertThat(captor.getValue().getRunId()).isEqualTo(11L);
        assertThat(captor.getValue().getTaskId()).isEqualTo(41L);
        assertThat(captor.getValue().getComment()).isEqualTo("looks good");
        assertThat(captor.getValue().getSourceHash()).hasSize(64);
        assertThat(result.label()).isEqualTo("ACCEPTED");
    }

    @Test
    void importsApprovalFactsWithoutPersistingRawReviewComment() {
        ChangeRequestFeedbackSnapshotVO snapshot = new ChangeRequestFeedbackSnapshotVO(71L, 9L, 41L, 51L,
                "RETURNED", null, "a".repeat(64), 61L,
                LocalDateTime.of(2026, 9, 21, 10, 0), 2, "b".repeat(64));
        when(taskFeign.getChangeRequestFeedbackSnapshot(71L)).thenReturn(Result.ok(snapshot));
        when(feedbackMapper.selectOne(any())).thenReturn(null);

        var result = service.importChangeRequest(71L);

        ArgumentCaptor<EvaluationFeedbackEntity> captor = ArgumentCaptor.forClass(EvaluationFeedbackEntity.class);
        verify(feedbackMapper).insert(captor.capture());
        assertThat(result.label()).isEqualTo("NEEDS_CHANGES");
        assertThat(captor.getValue().getComment()).isNull();
        assertThat(captor.getValue().getFactsJson()).contains("reviewCommentHash").doesNotContain("raw comment");
    }

    @Test
    void repeatedSnapshotImportReturnsExistingRecord() {
        ChangeRequestFeedbackSnapshotVO snapshot = new ChangeRequestFeedbackSnapshotVO(71L, 9L, null, null,
                "MERGED", "ALL", null, 61L, LocalDateTime.now(), 1, "b".repeat(64));
        EvaluationFeedbackEntity existing = new EvaluationFeedbackEntity();
        existing.setId(91L); existing.setSpaceId(9L); existing.setLabel("ACCEPTED");
        when(taskFeign.getChangeRequestFeedbackSnapshot(71L)).thenReturn(Result.ok(snapshot));
        when(feedbackMapper.selectOne(any())).thenReturn(existing);

        assertThat(service.importChangeRequest(71L).id()).isEqualTo(91L);
        verify(feedbackMapper, never()).insert(any(EvaluationFeedbackEntity.class));
    }
}
