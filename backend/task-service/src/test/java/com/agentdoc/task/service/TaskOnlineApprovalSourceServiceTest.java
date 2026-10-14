package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TaskOnlineOriginalProposalMapper;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.entity.TaskOnlineOriginalProposalEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskOnlineApprovalSourceServiceTest {
    private final TaskMapper tasks = mock(TaskMapper.class);
    private final TaskOnlineOriginalProposalMapper originals = mock(TaskOnlineOriginalProposalMapper.class);
    private final ChangeRequestService proposals = mock(ChangeRequestService.class);
    private final TaskOriginalEvidenceAccessService access = mock(TaskOriginalEvidenceAccessService.class);
    private final DocumentFeign documents = mock(DocumentFeign.class);
    private final TaskOnlineApprovalSourceService service = new TaskOnlineApprovalSourceService(tasks, originals, proposals, access, documents);
    private final OnlineOriginalInputVO input = new OnlineOriginalInputVO("61", "71", "10", "20", "11", "51", "a".repeat(64), "41", "FORMAL", 3L, "b".repeat(64), true);
    private ChangeRequestEntity request;
    private TaskOnlineOriginalProposalEntity original;
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "RS256").subject("501").claim("scope", "user").build()));
        request = new ChangeRequestEntity(); request.setId(91L); request.setSourceTaskId(61L); request.setSpaceId(10L); request.setDocumentId(41L);
        request.setProposedBy(20L); request.setProposedActorType(2); request.setStatus(ChangeRequestStatus.MERGED.getCode());
        request.setMergedVersion(4L); request.setMergedBy(501L); request.setMergedAt(LocalDateTime.parse("2026-10-10T20:00:00"));
        request.setChanges("人工修改后的载荷"); request.setResolvedContent("审批后正文");
        var task = new TaskEntity(); task.setId(61L); task.setSpaceId(10L); task.setOnlineAssignmentId(51L); task.setDocumentId(41L); task.setAgentId(20L); task.setAgentExecutionId(71L);
        when(proposals.requireRequest(91L)).thenReturn(request); when(tasks.selectById(61L)).thenReturn(task); when(access.input(61L, 10L, 71L)).thenReturn(input);
        when(documents.checkSpacePermission(eq(10L), anyString())).thenReturn(Result.ok());
        original = row(91); when(originals.selectList(any())).thenReturn(List.of(original)); when(proposals.agentProposalIds(61L)).thenReturn(List.of(91L));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private TaskOnlineOriginalProposalEntity row(long id) {
        var row = new TaskOnlineOriginalProposalEntity(); row.setId(id); row.setTaskId(61L); row.setSpaceId(10L); row.setAgentId(20L);
        row.setExperimentId(11L); row.setAssignmentId(51L); row.setDocumentId(41L); row.setBaseVersion(3L); row.setInputHash(input.inputHash()); row.setBindingHash(input.bindingHash());
        row.setOriginalChanges("[{\"op\":\"replace\",\"newText\":\"原始😀提案\"}]"); row.setPayloadHash(StableSnapshotUtils.sha256Utf8(row.getOriginalChanges()));
        row.setCapturedAt(LocalDateTime.parse("2026-10-10T19:00:00")); row.setIdentityHash(TaskOnlineOriginalProposalService.identityHash(row)); return row;
    }
    @Test void originalChangesSurviveApprovalEditsAndCarryExactExecutionAndMergeIdentity() {
        var result = service.get(91L); assertThat(result.reason()).isNull(); assertThat(result.changes().getFirst().newText()).isEqualTo("原始😀提案");
        assertThat(result.originalEvidenceHash()).isEqualTo(original.getPayloadHash()); assertThat(result.input()).isEqualTo(input);
        assertThat(result.mergedVersion()).isEqualTo(4L); assertThat(result.mergedBy()).isEqualTo("501"); verify(originals, never()).update(any(), any());
    }
    @Test void missingOriginalOrIncompleteWholeSetCannotBeFilledFromApproval() {
        when(originals.selectList(any())).thenReturn(List.of()); assertThat(service.get(91L).reason()).isEqualTo("ORIGINAL_EVIDENCE_UNAVAILABLE");
        when(originals.selectList(any())).thenReturn(List.of(original)); when(proposals.agentProposalIds(61L)).thenReturn(List.of(91L, 92L));
        assertThat(service.get(91L).changes()).isNull(); assertThat(service.get(91L).reason()).isEqualTo("ORIGINAL_EVIDENCE_UNAVAILABLE");
    }
    @Test void multipleOriginalProposalsAreNotBestPicked() {
        when(originals.selectList(any())).thenReturn(List.of(original, row(92))); when(proposals.agentProposalIds(61L)).thenReturn(List.of(91L, 92L));
        assertThat(service.get(91L).reason()).isEqualTo("MULTIPLE_SOURCES"); assertThat(service.get(91L).changes()).isNull();
    }
    @Test void corruptPayloadOrChangedProposalAgentIdentityIsExplicitlyInvalid() {
        original.setOriginalChanges("[]"); assertThat(service.get(91L).reason()).isEqualTo("ORIGINAL_EVIDENCE_INVALID");
        when(originals.selectList(any())).thenReturn(List.of(row(91))); request.setProposedBy(99L);
        assertThat(service.get(91L).reason()).isEqualTo("ORIGINAL_EVIDENCE_INVALID");
    }
    @Test void wrongFrozenBaseAndUnmergedApprovalCannotProduceDiffMetrics() {
        original.setBaseVersion(4L); original.setIdentityHash(TaskOnlineOriginalProposalService.identityHash(original));
        assertThat(service.get(91L).reason()).isEqualTo("BASE_VERSION_MISMATCH");
        when(originals.selectList(any())).thenReturn(List.of(row(91))); request.setStatus(ChangeRequestStatus.APPROVED.getCode());
        assertThat(service.get(91L).reason()).isEqualTo("NOT_MERGED");
    }
    @Test void currentTaskAndApprovalPermissionsAreCheckedBeforeOriginalRows() {
        when(access.input(61L, 10L, 71L)).thenThrow(new BusinessException(40300, "权限撤回"));
        assertThatThrownBy(() -> service.get(91L)).isInstanceOf(BusinessException.class); verifyNoInteractions(originals, documents); verify(proposals, never()).agentProposalIds(any());
        doReturn(input).when(access).input(61L, 10L, 71L); when(documents.checkSpacePermission(10L, "change_request:read")).thenReturn(Result.fail(40300, "审批读取权限撤回"));
        assertThatThrownBy(() -> service.get(91L)).isInstanceOf(BusinessException.class); verifyNoInteractions(originals); verify(proposals, never()).agentProposalIds(any());
    }
    @Test void incompleteExecutionHasNoOriginalComparisonAndAgentCannotUseHumanReadEndpoint() {
        when(access.input(61L, 10L, 71L)).thenReturn(new OnlineOriginalInputVO(input.taskId(), input.executionId(), input.spaceId(), input.agentId(), input.experimentId(), input.assignmentId(), input.bindingHash(), input.documentId(), input.documentType(), input.baseVersion(), input.inputHash(), false));
        assertThat(service.get(91L).reason()).isEqualTo("TASK_NOT_COMPLETED");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("agent").header("alg", "RS256").subject("61")
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_AGENT).claim(JwtConstant.CLAIM_ACTOR_TYPE, JwtConstant.ACTOR_AGENT).build()));
        assertThatThrownBy(() -> service.get(91L)).isInstanceOf(BusinessException.class);
    }
}
