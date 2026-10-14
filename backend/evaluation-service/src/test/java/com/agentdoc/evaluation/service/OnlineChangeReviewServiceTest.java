package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.DocumentFragmentVO;
import com.agentdoc.common.feign.vo.DocumentVersionExecutionContextVO;
import com.agentdoc.common.feign.vo.OnlineOriginalChangesVO;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.feign.vo.OnlineOriginalChangeItemVO;
import com.agentdoc.common.feign.vo.OnlineOriginalProposalPayloadVO;
import com.agentdoc.common.feign.vo.OnlineProposalReviewPreviewVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineOriginalChangeUtils;
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
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnlineChangeReviewServiceTest {
    private final OnlineExperimentMapper experiments = mock(OnlineExperimentMapper.class);
    private final OnlineAssignmentMapper assignments = mock(OnlineAssignmentMapper.class);
    private final SpaceAccessService access = mock(SpaceAccessService.class);
    private final TaskFeign tasks = mock(TaskFeign.class);
    private final DocumentFeign documents = mock(DocumentFeign.class);
    private final OnlineTextReviewService text = mock(OnlineTextReviewService.class);
    private final OnlineReviewExposureService exposures = mock(OnlineReviewExposureService.class);
    private final OnlineChangeReviewService service = new OnlineChangeReviewService(experiments,assignments,access,tasks,documents,text,exposures);
    private final String body = " 冻结😀\r\n正文 ";
    private final String hash = StableSnapshotUtils.sha256Utf8(body);
    private final String content = body + " 原始提案 ";
    private final String payload = "[{\"op\":\"append\",\"newText\":\" 原始提案 \"}]";
    private OnlineOriginalInputVO input;
    private OnlineAssignmentEntity assignment;
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg","RS256").subject("501").claim("scope","user").build()));
        var experiment = new OnlineExperimentEntity(); experiment.setId(11L); experiment.setSpaceId(10L); experiment.setManifestSchemaVersion(2);
        when(experiments.selectById(11L)).thenReturn(experiment);
        assignment = new OnlineAssignmentEntity(); assignment.setId(51L); assignment.setTaskId(61L); assignment.setExecutionId(71L);
        assignment.setSpaceId(10L); assignment.setAgentId(20L); assignment.setDocumentId(41L); assignment.setExperimentId(11L);
        assignment.setBindingSchemaVersion(2); assignment.setBindingHash("a".repeat(64)); assignment.setVariant("CANDIDATE");
        when(assignments.selectById(51L)).thenReturn(assignment); input("FORMAL"); formal(false);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void input(String type) {
        input = new OnlineOriginalInputVO("61","71","10","20","11","51","a".repeat(64),"41",type,3L,hash,true);
        when(tasks.getOnlineOriginalInput(61L,10L,71L)).thenReturn(Result.ok(input)); when(text.readInputContent(input)).thenReturn(body);
    }
    private OnlineOriginalChangesVO formal(boolean multiple) {
        var proof = OnlineOriginalChangeUtils.signed(input,"91","CHANGE_REQUEST",3L,null,StableSnapshotUtils.sha256Utf8(payload),StableSnapshotUtils.sha256Utf8(content),true,"2026-10-12T12:00");
        var bad = OnlineOriginalChangeUtils.signed(input,"92","CHANGE_REQUEST",3L,null,StableSnapshotUtils.sha256Utf8("[]"),null,false,"2026-10-12T12:00");
        var evidence = new OnlineOriginalChangesVO(2,"AVAILABLE",input,multiple ? List.of(proof,bad) : List.of(proof));
        when(tasks.getOnlineOriginalProposalPayloads(61L,10L,71L)).thenReturn(Result.ok(new OnlineOriginalProposalPayloadVO(evidence,
                multiple ? List.of(new OnlineOriginalProposalPayloadVO.Item("91",payload),new OnlineOriginalProposalPayloadVO.Item("92","[]")) : List.of(new OnlineOriginalProposalPayloadVO.Item("91",payload)))));
        var preview = new OnlineProposalReviewPreviewVO.Item(91L,true,proof.contentHash(),content);
        when(documents.previewOnlineChangesForReview(any())).thenReturn(Result.ok(new OnlineProposalReviewPreviewVO(body,
                multiple ? List.of(preview,new OnlineProposalReviewPreviewVO.Item(92L,false,null,null)) : List.of(preview))));
        return evidence;
    }
    private void draft(String original) {
        input("DRAFT"); var originalHash = StableSnapshotUtils.sha256Utf8(original);
        var proof = OnlineOriginalChangeUtils.signed(input,"101","DRAFT_DOCUMENT",3L,4L,originalHash,originalHash,true,"2026-10-12T12:00");
        when(tasks.getOnlineOriginalChanges(61L,10L,71L)).thenReturn(Result.ok(new OnlineOriginalChangesVO(2,"AVAILABLE",input,List.of(proof))));
        when(documents.getVersionExecutionContext(41L,4L,originalHash)).thenReturn(Result.ok(new DocumentVersionExecutionContextVO(41L,4L,originalHash,(long)original.length())));
        when(documents.readVersionFragment(41L,4L,originalHash,0,original.length())).thenReturn(Result.ok(new DocumentFragmentVO(41L,original,0,original.length(),original.length())));
    }
    @Test void completeFormalCollectionKeepsInvalidOriginalAndHidesGroupAndConfig() {
        var source = formal(true); var result = service.read("11","51"); assertThat(result.inputContent()).isEqualTo(body);
        assertThat(result.originalSetId()).isEqualTo("51"); assertThat(result.originalSetHash()).isEqualTo(OnlineOriginalChangeUtils.setIdentityHash(source));
        assertThat(result.originals()).hasSize(2); assertThat(result.originals().getFirst().originalContent()).isEqualTo(content);
        assertThat(result.originals().getLast().originalChanges()).isEqualTo("[]"); assertThat(result.originals().getLast().structureValid()).isFalse();
        assertThat(result.originals().getLast().originalContent()).isNull(); assertThat(result.knowledgeStatus()).isEqualTo("UNKNOWN");
        var fields = new HashSet<String>(); JsonUtils.parse(JsonUtils.toJson(result),JsonNode.class).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("assignmentId","taskId","executionId","documentId","baseVersion","inputHash","inputContent","originalSetId","originalSetHash","originals","knowledgeStatus");
        verify(documents,times(1)).previewOnlineChangesForReview(argThat(request -> request.proposals().size() == 2 && request.baseContentSha256().equals(hash)));
        verify(exposures,never()).record(any(),any(),any(),any());
    }
    @Test void payloadHashTamperingOrSubsetCannotBecomeOriginalPreview() {
        var source = formal(true);
        when(tasks.getOnlineOriginalProposalPayloads(61L,10L,71L)).thenReturn(Result.ok(new OnlineOriginalProposalPayloadVO(source,List.of(new OnlineOriginalProposalPayloadVO.Item("91",payload)))));
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class); verifyNoInteractions(documents);
        when(tasks.getOnlineOriginalProposalPayloads(61L,10L,71L)).thenReturn(Result.ok(new OnlineOriginalProposalPayloadVO(source,List.of(new OnlineOriginalProposalPayloadVO.Item("91","[]"),new OnlineOriginalProposalPayloadVO.Item("92","[]")))));
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class); verifyNoInteractions(documents);
    }
    @Test void previewBodyHashInputOrStructureDriftCannotBeReturned() {
        when(documents.previewOnlineChangesForReview(any())).thenReturn(Result.ok(new OnlineProposalReviewPreviewVO(body,List.of(new OnlineProposalReviewPreviewVO.Item(91L,true,StableSnapshotUtils.sha256Utf8(content),"审批后正文")))));
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class);
        when(documents.previewOnlineChangesForReview(any())).thenReturn(Result.ok(new OnlineProposalReviewPreviewVO("当前输入",List.of(new OnlineProposalReviewPreviewVO.Item(91L,true,StableSnapshotUtils.sha256Utf8(content),content)))));
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class);
        when(documents.previewOnlineChangesForReview(any())).thenReturn(Result.ok(new OnlineProposalReviewPreviewVO(body,List.of(new OnlineProposalReviewPreviewVO.Item(91L,false,null,null)))));
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class);
    }
    @Test void draftReadsOnlyTheProvenFinalVersionAndEmptyContentIsValid() {
        draft(" 原始😀\r\n草稿 "); var result = service.read("11","51");
        assertThat(result.originals().getFirst().originalContent()).isEqualTo(" 原始😀\r\n草稿 ");
        assertThat(result.originals().getFirst().outputVersion()).isEqualTo(4L); assertThat(result.originals().getFirst().originalChanges()).isNull();
        assertThat(result.originalSetHash()).isEqualTo(OnlineOriginalChangeUtils.setIdentityHash(tasks.getOnlineOriginalChanges(61L,10L,71L).data()));
        verify(tasks,never()).getOnlineOriginalProposalPayloads(anyLong(),anyLong(),anyLong()); verify(documents,never()).previewOnlineChangesForReview(any());
        draft(""); assertThat(service.read("11","51").originals().getFirst().originalContent()).isEmpty();
    }
    @Test void draftMissingMultipleOrIncompleteFragmentDoesNotUseCurrentContent() {
        draft("原始草稿"); var proof = tasks.getOnlineOriginalChanges(61L,10L,71L).data().items().getFirst();
        when(tasks.getOnlineOriginalChanges(61L,10L,71L)).thenReturn(Result.ok(new OnlineOriginalChangesVO(2,"AVAILABLE",input,List.of(proof,proof))));
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class); verifyNoInteractions(documents);
        draft("原始草稿"); when(documents.readVersionFragment(41L,4L,proof.contentHash(),0,4)).thenReturn(Result.ok(new DocumentFragmentVO(41L,"短",0,1,4)));
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class);
        when(tasks.getOnlineOriginalChanges(61L,10L,71L)).thenReturn(Result.ok(new OnlineOriginalChangesVO(2,"EVIDENCE_UNAVAILABLE",input,List.of())));
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class);
    }
    @Test void lateRevocationOrBindingChangePreventsReturningFetchedBodies() {
        when(tasks.getOnlineOriginalInput(61L,10L,71L)).thenReturn(Result.ok(input)).thenThrow(new BusinessException(40300,"权限撤回"));
        assertThatThrownBy(() -> service.read("11","51")).hasMessageContaining("撤回"); verifyNoInteractions(exposures);
        doReturn(Result.ok(input)).when(tasks).getOnlineOriginalInput(61L,10L,71L);
        var changed = new OnlineAssignmentEntity(); changed.setId(51L); when(assignments.selectById(51L)).thenReturn(assignment).thenReturn(changed);
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class);
    }
    @Test void actualHumanAndCurrentPermissionsPrecedeBodyReads() {
        doThrow(new BusinessException(40300,"权限撤回")).when(access).requirePermission(10L,"evaluation:run");
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class); verifyNoInteractions(tasks,documents);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("agent").header("alg","RS256").subject("61").claim("scope","agent").build()));
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class);
    }
    @Test void knownExposureRemainsNonBlindedWithoutWritingNewExposure() {
        var exposure = new OnlineReviewExposureEntity(); exposure.setSpaceId(10L); when(exposures.known(11L,501L)).thenReturn(exposure);
        assertThat(service.read("11","51").knowledgeStatus()).isEqualTo("NON_BLINDED"); exposure.setSpaceId(99L);
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class); verify(exposures,never()).record(any(),any(),any(),any());
    }
    @Test void oversizedRawPayloadIsRejectedBeforePreviewAndOversizedDraftIsNotTruncated() {
        String raw = " ".repeat(8_000_001);
        var proof = OnlineOriginalChangeUtils.signed(input,"91","CHANGE_REQUEST",3L,null,StableSnapshotUtils.sha256Utf8(raw),null,false,"2026-10-12T12:00");
        when(tasks.getOnlineOriginalProposalPayloads(61L,10L,71L)).thenReturn(Result.ok(new OnlineOriginalProposalPayloadVO(new OnlineOriginalChangesVO(2,"AVAILABLE",input,List.of(proof)),List.of(new OnlineOriginalProposalPayloadVO.Item("91",raw)))));
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class); verifyNoInteractions(documents);
        draft("x".repeat(2_000_001)); assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class);
        verify(documents,never()).readVersionFragment(anyLong(),anyLong(),anyString(),anyLong(),anyInt());
    }
    @Test void unfinishedTaskOrOtherExperimentAssignmentCannotFetchBody() {
        when(tasks.getOnlineOriginalInput(61L,10L,71L)).thenReturn(Result.ok(new OnlineOriginalInputVO("61","71","10","20","11","51","a".repeat(64),"41","FORMAL",3L,hash,false)));
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class);
        verify(tasks,never()).getOnlineOriginalProposalPayloads(anyLong(),anyLong(),anyLong()); verifyNoInteractions(documents);
        assignment.setExperimentId(12L); assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class);
    }
    @Test void forgedProofOrPreviewSubsetCannotBeReturned() {
        var evidence = formal(false); var proof = evidence.items().getFirst();
        var forged = new OnlineOriginalChangeItemVO(proof.evidenceId(),proof.outputType(),proof.baseVersion(),proof.outputVersion(),proof.payloadHash(),proof.contentHash(),proof.structureValid(),proof.capturedAt(),"0".repeat(64));
        when(tasks.getOnlineOriginalProposalPayloads(61L,10L,71L)).thenReturn(Result.ok(new OnlineOriginalProposalPayloadVO(new OnlineOriginalChangesVO(2,"AVAILABLE",input,List.of(forged)),List.of(new OnlineOriginalProposalPayloadVO.Item("91",payload)))));
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class); verifyNoInteractions(documents);
        formal(true); when(documents.previewOnlineChangesForReview(any())).thenReturn(Result.ok(new OnlineProposalReviewPreviewVO(body,List.of(new OnlineProposalReviewPreviewVO.Item(91L,true,proof.contentHash(),content)))));
        assertThatThrownBy(() -> service.read("11","51")).isInstanceOf(BusinessException.class);
    }
}
