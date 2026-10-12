package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.ChangeRequestFeedbackSnapshotVO;
import com.agentdoc.common.feign.vo.OnlineApprovalSourceVO;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.utils.OnlineApprovalDifferenceUtils;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.enums.EvaluationFeedbackLabel;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationFeedbackMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.pojo.dto.EvaluationFeedbackCreateDTO;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
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
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationFeedbackServiceTest {
    @Mock private EvaluationFeedbackMapper feedbackMapper;
    @Mock private EvaluationCaseRunMapper caseRunMapper;
    @Mock private EvaluationCaseAttemptMapper attemptMapper;
    @Mock private EvaluationEvidenceReferenceMapper evidenceMapper;
    @Mock private EvaluationResultMapper resultMapper;
    @Mock private SpaceAccessService spaceAccessService;
    @Mock private TaskFeign taskFeign;
    @Mock private OnlineAssignmentMapper onlineAssignments;

    private EvaluationFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationFeedbackService(feedbackMapper, caseRunMapper, attemptMapper, evidenceMapper,
                resultMapper, spaceAccessService, taskFeign, onlineAssignments);
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
        attempt.setExecutionTaskId(41L);
        when(caseRunMapper.selectById(21L)).thenReturn(caseRun);
        when(attemptMapper.selectById(31L)).thenReturn(attempt);

        var result = service.create(new EvaluationFeedbackCreateDTO(9L, 21L, null, null,
                EvaluationFeedbackLabel.ACCEPTED, null, " looks good ", null));

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
                LocalDateTime.of(2026, 9, 21, 10, 0), 2, "b".repeat(64), null);
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
                "MERGED", "ALL", null, 61L, LocalDateTime.now(), 1, "b".repeat(64), null);
        EvaluationFeedbackEntity existing = new EvaluationFeedbackEntity();
        existing.setId(91L); existing.setSpaceId(9L); existing.setLabel("ACCEPTED");
        when(taskFeign.getChangeRequestFeedbackSnapshot(71L)).thenReturn(Result.ok(snapshot));
        when(feedbackMapper.selectOne(any())).thenReturn(existing);

        assertThat(service.importChangeRequest(71L).id()).isEqualTo(91L);
        verify(feedbackMapper, never()).insert(any(EvaluationFeedbackEntity.class));
    }

    private ChangeRequestFeedbackSnapshotVO onlineSnapshot() {
        var input = new OnlineOriginalInputVO("41", "51", "9", "20", "10", "81", "a".repeat(64), "61", "FORMAL", 3L, "b".repeat(64), true);
        var source = new OnlineApprovalSourceVO(input, "71", "MERGED", "c".repeat(64), "d".repeat(64), 3L, null, 4L, "501", "2026-10-10T20:00:00", null);
        var difference = OnlineApprovalDifferenceUtils.signed(source, null, "e".repeat(64), "f".repeat(64), 1, 1, 1, new BigDecimal("0.25"));
        var value = new ChangeRequestFeedbackSnapshotVO(71L, 9L, 41L, 51L, "MERGED", "EDITED", "a".repeat(64), 501L, LocalDateTime.of(2026, 10, 10, 20, 0), 1, null, difference);
        return new ChangeRequestFeedbackSnapshotVO(value.changeRequestId(), value.spaceId(), value.taskId(), value.executionId(), value.status(), value.resolutionType(),
                value.reviewCommentHash(), value.reviewedBy(), value.reviewedAt(), value.revisionNo(), OnlineApprovalDifferenceUtils.feedbackHash(value), difference);
    }
    private OnlineAssignmentEntity assignment() {
        var value = new OnlineAssignmentEntity(); value.setId(81L); value.setSpaceId(9L); value.setTaskId(41L); value.setExecutionId(51L);
        value.setExperimentId(10L); value.setAgentId(20L); value.setDocumentId(61L); value.setBindingSchemaVersion(2); value.setBindingHash("a".repeat(64)); return value;
    }
    @Test void onlineApprovalAddsProofButCannotFillOriginalQualityOrBlindness() {
        when(taskFeign.getChangeRequestFeedbackSnapshot(71L)).thenReturn(Result.ok(onlineSnapshot())); when(onlineAssignments.selectById(81L)).thenReturn(assignment());
        var result = service.importChangeRequest(71L);
        var captor = ArgumentCaptor.forClass(EvaluationFeedbackEntity.class); verify(feedbackMapper).insert(captor.capture());
        var value = captor.getValue(); assertThat(value.getOnlineAssignmentId()).isEqualTo(81L); assertThat(value.getOriginalEvidenceHash()).isEqualTo("c".repeat(64));
        assertThat(value.getRubricScore()).isNull(); assertThat(value.getBlindness()).isNull(); assertThat(result.score()).isNull();
        assertThat(value.getRunId()).isNull(); assertThat(value.getCaseRunId()).isNull();
        assertThat(value.getFactsJson()).contains("onlineApprovalDifference", "originalContentHash", "humanModificationRatio").doesNotContain("原始正文", "审批意见原文");
        assertThat(value.getFactsJson().length()).isLessThanOrEqualTo(2000);
    }
    @Test void mismatchedActualAssignmentIsRejectedBeforeAnyWriteOrRetryLookup() {
        var actual = assignment(); actual.setExecutionId(99L); when(onlineAssignments.selectById(81L)).thenReturn(actual);
        when(taskFeign.getChangeRequestFeedbackSnapshot(71L)).thenReturn(Result.ok(onlineSnapshot()));
        assertThatThrownBy(() -> service.importChangeRequest(71L)).isInstanceOf(BusinessException.class); verifyNoInteractions(feedbackMapper);
    }
    @Test void changedEnvelopeCannotReuseItsOldIdentityHash() {
        var snapshot = onlineSnapshot();
        when(taskFeign.getChangeRequestFeedbackSnapshot(71L)).thenReturn(Result.ok(new ChangeRequestFeedbackSnapshotVO(71L, 9L, 41L, 51L,
                "REJECTED", snapshot.resolutionType(), snapshot.reviewCommentHash(), snapshot.reviewedBy(), snapshot.reviewedAt(), snapshot.revisionNo(), snapshot.sourceHash(), snapshot.onlineApprovalDifference())));
        assertThatThrownBy(() -> service.importChangeRequest(71L)).isInstanceOf(BusinessException.class); verifyNoInteractions(feedbackMapper, onlineAssignments);
    }
}
