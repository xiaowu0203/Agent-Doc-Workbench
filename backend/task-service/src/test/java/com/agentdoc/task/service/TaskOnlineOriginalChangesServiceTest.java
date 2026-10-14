package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.feign.vo.OnlineChangePreviewVO;
import com.agentdoc.common.utils.OnlineOriginalChangeUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.task.mapper.TaskOnlineOriginalProposalMapper;
import com.agentdoc.task.pojo.entity.TaskOnlineOriginalProposalEntity;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskOnlineOriginalChangesServiceTest {
    private final TaskOriginalEvidenceAccessService access = mock(TaskOriginalEvidenceAccessService.class);
    private final TaskOnlineOriginalProposalMapper originals = mock(TaskOnlineOriginalProposalMapper.class);
    private final ChangeRequestService proposals = mock(ChangeRequestService.class);
    private final DocumentFeign documents = mock(DocumentFeign.class);
    private final TaskOnlineOriginalChangesService service = new TaskOnlineOriginalChangesService(access,originals,proposals,documents);
    private final OnlineOriginalInputVO input = new OnlineOriginalInputVO("61","71","10","20","11","51","a".repeat(64),"41","FORMAL",3L,"b".repeat(64),true);
    private TaskOnlineOriginalProposalEntity row(long id) {
        var row = new TaskOnlineOriginalProposalEntity(); row.setId(id); row.setTaskId(61L); row.setSpaceId(10L); row.setAgentId(20L);
        row.setExperimentId(11L); row.setAssignmentId(51L); row.setDocumentId(41L); row.setBaseVersion(3L); row.setInputHash(input.inputHash());
        row.setBindingHash(input.bindingHash()); row.setOriginalChanges("[{\"op\":\"replace\",\"newText\":\"原始提案\"}]");
        row.setPayloadHash(StableSnapshotUtils.sha256Utf8(row.getOriginalChanges())); row.setCapturedAt(LocalDateTime.parse("2026-10-10T20:00:00"));
        row.setIdentityHash(TaskOnlineOriginalProposalService.identityHash(row)); return row;
    }
    @Test void completeOriginalSetUsesOneBatchRpcAndIncludesFailedProposal() {
        when(access.input(61L,10L,71L)).thenReturn(input); when(proposals.agentProposalIds(61L)).thenReturn(List.of(91L,92L));
        when(originals.selectList(any())).thenReturn(List.of(row(91),row(92)));
        when(documents.previewOnlineChanges(any())).thenReturn(Result.ok(List.of(new OnlineChangePreviewVO(91L,true,"c".repeat(64)),new OnlineChangePreviewVO(92L,false,null))));
        var result = service.read(61L,10L,71L); assertThat(result.items()).hasSize(2); assertThat(result.items().getLast().structureValid()).isFalse();
        assertThat(result.items()).allMatch(item -> OnlineOriginalChangeUtils.valid(input,item));
        verify(documents,times(1)).previewOnlineChanges(argThat(request -> request.proposals().size() == 2 && request.baseVersion() == 3L && request.baseContentSha256().equals(input.inputHash())));
    }
    @Test void missingOneOriginalDoesNotSelectRemainingGoodProposalOrReadApprovalBody() {
        when(access.input(61L,10L,71L)).thenReturn(input); when(proposals.agentProposalIds(61L)).thenReturn(List.of(91L,92L));
        when(originals.selectList(any())).thenReturn(List.of(row(91)));
        assertThat(service.read(61L,10L,71L).state()).isEqualTo("EVIDENCE_UNAVAILABLE"); verifyNoInteractions(documents);
    }
    @Test void tamperedOriginalPayloadIsInvalidBeforePreview() {
        when(access.input(61L,10L,71L)).thenReturn(input); when(proposals.agentProposalIds(61L)).thenReturn(List.of(91L));
        var row = row(91); row.setOriginalChanges("[]"); when(originals.selectList(any())).thenReturn(List.of(row));
        assertThat(service.read(61L,10L,71L).state()).isEqualTo("ORIGINAL_EVIDENCE_INVALID"); verifyNoInteractions(documents);
    }
    @Test void currentPermissionRejectionPrecedesOriginalRead() {
        when(access.input(61L,10L,71L)).thenThrow(new BusinessException(40300,"权限被撤回"));
        assertThatThrownBy(() -> service.read(61L,10L,71L)).isInstanceOf(BusinessException.class); verifyNoInteractions(originals,proposals,documents);
    }
    @Test void payloadReadPreservesAllOriginalJsonIncludingTheInvalidProposal() {
        when(access.input(61L,10L,71L)).thenReturn(input); when(proposals.agentProposalIds(61L)).thenReturn(List.of(91L,92L));
        var first = row(91); var second = row(92); when(originals.selectList(any())).thenReturn(List.of(first,second));
        when(documents.previewOnlineChanges(any())).thenReturn(Result.ok(List.of(new OnlineChangePreviewVO(91L,true,"c".repeat(64)),new OnlineChangePreviewVO(92L,false,null))));
        var result = service.payloads(61L,10L,71L);
        assertThat(result.items()).extracting(item -> item.evidenceId()).containsExactly("91","92");
        assertThat(result.items().getFirst().originalChanges()).isEqualTo(first.getOriginalChanges());
        assertThat(result.items().getLast().originalChanges()).isEqualTo(second.getOriginalChanges());
        assertThat(result.evidence().items().getLast().structureValid()).isFalse();
        verify(documents,times(1)).previewOnlineChanges(any()); verify(access,times(2)).input(61L,10L,71L);
    }
    @Test void payloadChangeBetweenProofAndBodyReadCannotBeReturned() {
        when(access.input(61L,10L,71L)).thenReturn(input); when(proposals.agentProposalIds(61L)).thenReturn(List.of(91L));
        var altered = row(91); altered.setOriginalChanges("[]"); altered.setPayloadHash(StableSnapshotUtils.sha256Utf8("[]"));
        altered.setIdentityHash(TaskOnlineOriginalProposalService.identityHash(altered));
        when(originals.selectList(any())).thenReturn(List.of(row(91))).thenReturn(List.of(altered));
        when(documents.previewOnlineChanges(any())).thenReturn(Result.ok(List.of(new OnlineChangePreviewVO(91L,true,"c".repeat(64)))));
        assertThatThrownBy(() -> service.payloads(61L,10L,71L)).isInstanceOf(BusinessException.class);
    }
    @Test void lateRevocationOrChangedCollectionBlocksPayloadRead() {
        when(access.input(61L,10L,71L)).thenReturn(input).thenThrow(new BusinessException(40300,"权限撤回"));
        when(proposals.agentProposalIds(61L)).thenReturn(List.of(91L)); when(originals.selectList(any())).thenReturn(List.of(row(91)));
        when(documents.previewOnlineChanges(any())).thenReturn(Result.ok(List.of(new OnlineChangePreviewVO(91L,true,"c".repeat(64)))));
        assertThatThrownBy(() -> service.payloads(61L,10L,71L)).hasMessageContaining("撤回");
        doReturn(input).when(access).input(61L,10L,71L); when(proposals.agentProposalIds(61L)).thenReturn(List.of(91L)).thenReturn(List.of(91L,92L));
        assertThatThrownBy(() -> service.payloads(61L,10L,71L)).isInstanceOf(BusinessException.class);
    }
}
