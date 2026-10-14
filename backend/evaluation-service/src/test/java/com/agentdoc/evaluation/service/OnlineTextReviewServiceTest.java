package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.AgentOnlineOriginalTextVO;
import com.agentdoc.common.feign.vo.DocumentFragmentVO;
import com.agentdoc.common.feign.vo.DocumentVersionExecutionContextVO;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineOriginalTextUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineReviewExposureEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_RUN;

class OnlineTextReviewServiceTest {
    private final OnlineExperimentMapper experiments = mock(OnlineExperimentMapper.class);
    private final OnlineAssignmentMapper assignments = mock(OnlineAssignmentMapper.class);
    private final SpaceAccessService access = mock(SpaceAccessService.class);
    private final TaskFeign tasks = mock(TaskFeign.class);
    private final AgentFeign agents = mock(AgentFeign.class);
    private final DocumentFeign documents = mock(DocumentFeign.class);
    private final OnlineReviewExposureService exposures = mock(OnlineReviewExposureService.class);
    private final OnlineQualityFeedbackService quality = new OnlineQualityFeedbackService(assignments, experiments, access, tasks, agents, mock(OnlineQualityFeedbackPersistenceService.class), mock(OnlineBlindReviewReceiptService.class), mock(OnlineSafetyFeedbackService.class));
    private final OnlineTextReviewService service = new OnlineTextReviewService(experiments, assignments, access, tasks, documents, quality, exposures);
    private final String body = " 冻结😀正文\r\n";
    private final String hash = StableSnapshotUtils.sha256Utf8(body);
    private OnlineAssignmentEntity assignment;
    private OnlineOriginalInputVO input;
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "RS256").subject("501").claim("scope", "user").build()));
        var experiment = new OnlineExperimentEntity(); experiment.setId(11L); experiment.setSpaceId(10L); experiment.setManifestSchemaVersion(2);
        when(experiments.selectById(11L)).thenReturn(experiment);
        assignment = new OnlineAssignmentEntity(); assignment.setId(51L); assignment.setTaskId(61L); assignment.setExecutionId(71L);
        assignment.setSpaceId(10L); assignment.setAgentId(20L); assignment.setDocumentId(41L); assignment.setExperimentId(11L);
        assignment.setBindingSchemaVersion(2); assignment.setBindingHash("a".repeat(64)); assignment.setVariant("CANDIDATE");
        when(assignments.selectById(51L)).thenReturn(assignment);
        input = new OnlineOriginalInputVO("61", "71", "10", "20", "11", "51", "a".repeat(64), "41", "FORMAL", 3L, hash, true);
        when(tasks.getOnlineOriginalInput(61L, 10L, 71L)).thenReturn(Result.ok(input));
        original(" 原始完整文本\r\n");
        when(documents.getVersionExecutionContext(41L, 3L, hash)).thenReturn(Result.ok(new DocumentVersionExecutionContextVO(41L, 3L, hash, (long) body.length())));
        when(documents.readVersionFragment(41L, 3L, hash, 0, body.length())).thenReturn(Result.ok(new DocumentFragmentVO(41L, body, 0, body.length(), body.length())));
    }
    private void original(String text) {
        var unsigned = new AgentOnlineOriginalTextVO(2, "AVAILABLE", "71", "61", "71", "10", "20", "11", "51", "a".repeat(64),
                StableSnapshotUtils.sha256Utf8(text), null, "2026-10-12T12:00", text);
        when(agents.getOnlineOriginalText(61L, 10L)).thenReturn(Result.ok(new AgentOnlineOriginalTextVO(2, "AVAILABLE", "71", "61", "71", "10", "20", "11", "51", "a".repeat(64),
                unsigned.contentHash(), OnlineOriginalTextUtils.identityHash(unsigned), unsigned.capturedAt(), text)));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void returnsExactOriginalAndFrozenBodyWithoutAnyGroupOrConfigurationProjection() {
        var view = service.read("11", "51"); assertThat(view.inputContent()).isEqualTo(body); assertThat(view.originalText()).isEqualTo(" 原始完整文本\r\n");
        assertThat(view.knowledgeStatus()).isEqualTo("UNKNOWN");
        var fields = new HashSet<String>(); JsonUtils.parse(JsonUtils.toJson(view), JsonNode.class).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("assignmentId", "taskId", "executionId", "documentId", "baseVersion", "inputHash", "inputContent", "originalEvidenceId", "originalEvidenceHash", "originalText", "knowledgeStatus");
        verify(tasks, times(2)).getOnlineOriginalInput(61L, 10L, 71L); verify(exposures, never()).record(any(), any(), any(), any());
    }
    @Test void knownExposureReturnsNonBlindedButMissingHistoryCannotClaimBlinded() {
        var known = new OnlineReviewExposureEntity(); known.setSpaceId(10L); when(exposures.known(11L, 501L)).thenReturn(known);
        assertThat(service.read("11", "51").knowledgeStatus()).isEqualTo("NON_BLINDED");
        known.setSpaceId(99L); assertThatThrownBy(() -> service.read("11", "51")).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsIncompleteFragmentOrCurrentVersionBodyInsteadOfFrozenContent() {
        when(documents.readVersionFragment(41L, 3L, hash, 0, body.length())).thenReturn(Result.ok(new DocumentFragmentVO(41L, "短摘要", 0, 3, body.length())));
        assertThatThrownBy(() -> service.read("11", "51")).isInstanceOf(BusinessException.class);
        when(documents.readVersionFragment(41L, 3L, hash, 0, body.length())).thenReturn(Result.ok(new DocumentFragmentVO(41L, "x".repeat(body.length()), 0, body.length(), body.length())));
        assertThatThrownBy(() -> service.read("11", "51")).isInstanceOf(BusinessException.class);
    }
    @Test void incompleteTaskOrWrongBindingCannotFetchOriginalBody() {
        when(tasks.getOnlineOriginalInput(61L, 10L, 71L)).thenReturn(Result.ok(new OnlineOriginalInputVO("61", "71", "10", "20", "11", "51", "a".repeat(64), "41", "FORMAL", 3L, hash, false)));
        assertThatThrownBy(() -> service.read("11", "51")).isInstanceOf(BusinessException.class); verifyNoInteractions(agents, documents);
        assignment.setBindingHash("b".repeat(64)); assertThatThrownBy(() -> service.read("11", "51")).isInstanceOf(BusinessException.class);
    }
    @Test void revocationAtTheFinalReadPreventsReturningAlreadyFetchedBody() {
        when(tasks.getOnlineOriginalInput(61L, 10L, 71L)).thenReturn(Result.ok(input)).thenThrow(new BusinessException(403, "原文档移出空间"));
        assertThatThrownBy(() -> service.read("11", "51")).hasMessageContaining("移出空间");
        verify(documents).readVersionFragment(41L, 3L, hash, 0, body.length()); verifyNoInteractions(exposures);
    }
    @Test void currentPermissionAndActualHumanAreRequiredBeforeSourceReads() {
        doThrow(new BusinessException(403, "权限不足")).when(access).requirePermission(10L, EVALUATION_RUN);
        assertThatThrownBy(() -> service.read("11", "51")).isInstanceOf(BusinessException.class); verifyNoInteractions(tasks, agents, documents);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test-agent").header("alg", "RS256").subject("61").claim("scope", "agent").build()));
        assertThatThrownBy(() -> service.read("11", "51")).isInstanceOf(BusinessException.class);
    }
    @Test void oversizedInputOrOriginalIsRejectedAndNeverTruncatedIntoAReview() {
        when(documents.getVersionExecutionContext(41L, 3L, hash)).thenReturn(Result.ok(new DocumentVersionExecutionContextVO(41L, 3L, hash, 2_000_001L)));
        assertThatThrownBy(() -> service.read("11", "51")).isInstanceOf(BusinessException.class);
        original("x".repeat(2_000_001)); assertThatThrownBy(() -> service.read("11", "51")).isInstanceOf(BusinessException.class);
        verify(documents, never()).readVersionFragment(anyLong(), anyLong(), anyString(), anyLong(), anyInt());
    }
    @Test void missingTextDoesNotFallBackToSummaryAndEmptyOriginalRemainsValid() {
        when(agents.getOnlineOriginalText(61L, 10L)).thenReturn(Result.ok(null)); assertThatThrownBy(() -> service.read("11", "51")).isInstanceOf(BusinessException.class);
        original(""); assertThat(service.read("11", "51").originalText()).isEmpty();
    }
}
