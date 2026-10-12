package com.agentdoc.evaluation.service;
import com.agentdoc.evaluation.pojo.vo.EvaluationFeedbackVO;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.AgentOnlineOriginalTextVO;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.feign.vo.OnlineOriginalChangesVO;
import com.agentdoc.common.utils.OnlineOriginalChangeUtils;
import com.agentdoc.common.utils.OnlineOriginalTextUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.evaluation.enums.EvaluationFeedbackLabel;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.dto.EvaluationFeedbackCreateDTO;
import com.agentdoc.evaluation.pojo.dto.OnlineQualityFeedbackDTO;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_RUN;
import static com.agentdoc.common.constant.SpacePermissionConstant.SPACE_READ;

class OnlineQualityFeedbackServiceTest {
    private final OnlineAssignmentMapper assignments = mock(OnlineAssignmentMapper.class);
    private final OnlineExperimentMapper experiments = mock(OnlineExperimentMapper.class);
    private final SpaceAccessService access = mock(SpaceAccessService.class);
    private final TaskFeign tasks = mock(TaskFeign.class);
    private final AgentFeign agents = mock(AgentFeign.class);
    private final OnlineQualityFeedbackPersistenceService persistence = mock(OnlineQualityFeedbackPersistenceService.class);
    private final OnlineBlindReviewReceiptService receipts = mock(OnlineBlindReviewReceiptService.class);
    private final OnlineSafetyFeedbackService safety = mock(OnlineSafetyFeedbackService.class);
    private final OnlineQualityFeedbackService service = new OnlineQualityFeedbackService(assignments, experiments, access, tasks, agents, persistence, receipts, safety);
    private OnlineAssignmentEntity assignment;
    private OnlineOriginalInputVO input;
    private AgentOnlineOriginalTextVO text;
    @BeforeEach void setup() {
        when(receipts.require(any(), any(), any())).thenThrow(new BusinessException(409, "ONLINE_QUALITY_BLIND_REVIEW_NOT_READY"));
        var persisted = mock(EvaluationFeedbackVO.class); when(persisted.id()).thenReturn(91L);
        when(persistence.append(any(), anyString(), any(), anyLong())).thenReturn(persisted);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "RS256").subject("501").claim("scope", "user").build()));
        assignment = new OnlineAssignmentEntity(); assignment.setId(51L); assignment.setTaskId(61L); assignment.setExecutionId(71L);
        assignment.setSpaceId(10L); assignment.setAgentId(20L); assignment.setExperimentId(11L); assignment.setDocumentId(41L);
        assignment.setBindingSchemaVersion(2); assignment.setBindingHash("a".repeat(64));
        var experiment = new OnlineExperimentEntity(); experiment.setId(11L); experiment.setSpaceId(10L); experiment.setManifestSchemaVersion(2);
        when(assignments.selectById(51L)).thenReturn(assignment); when(experiments.selectById(11L)).thenReturn(experiment);
        input = new OnlineOriginalInputVO("61", "71", "10", "20", "11", "51", "a".repeat(64), "41", "FORMAL", 3L, "b".repeat(64), true);
        when(tasks.getOnlineOriginalInput(61L, 10L, 71L)).thenReturn(Result.ok(input));
        var unsigned = new AgentOnlineOriginalTextVO(2, "AVAILABLE", "71", "61", "71", "10", "20", "11", "51", "a".repeat(64),
                StableSnapshotUtils.sha256Utf8("原始正文"), null, "2026-10-12T12:00", "原始正文");
        text = new AgentOnlineOriginalTextVO(2, "AVAILABLE", "71", "61", "71", "10", "20", "11", "51", "a".repeat(64),
                unsigned.contentHash(), OnlineOriginalTextUtils.identityHash(unsigned), unsigned.capturedAt(), unsigned.originalText());
        when(agents.getOnlineOriginalText(61L, 10L)).thenReturn(Result.ok(text));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private EvaluationFeedbackCreateDTO request(String type, String id, String hash, String blind, boolean safety) {
        return new EvaluationFeedbackCreateDTO(10L, null, null, null, EvaluationFeedbackLabel.REJECTED, BigDecimal.ZERO, null,
                new OnlineQualityFeedbackDTO("51", type, id, hash, 0, blind, null, List.of(), safety, safety ? "原始产物暴露敏感信息" : null));
    }
    private EvaluationFeedbackCreateDTO request() { return request("ORIGINAL_TEXT", "71", text.contentHash(), "UNKNOWN", false); }
    @Test void verifiedZeroUsesActualInputAndHumanWithoutBodyOrOfflineIdentity() {
        service.create(request());
        verify(access).requirePermission(10L, SPACE_READ); verify(access).requirePermission(10L, EVALUATION_RUN);
        verify(persistence).append(input, text.identityHash(), request(), 501L); verify(access, never()).requireOwner(anyLong());
    }
    @Test void missingOrIncompleteOriginalCannotCreateZero() {
        when(agents.getOnlineOriginalText(61L, 10L)).thenReturn(Result.ok(null));
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class);
        when(tasks.getOnlineOriginalInput(61L, 10L, 71L)).thenReturn(Result.ok(new OnlineOriginalInputVO("61", "71", "10", "20", "11", "51", "a".repeat(64), "41", "FORMAL", 3L, "b".repeat(64), false)));
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class);
        verifyNoInteractions(persistence);
    }
    @Test void wrongHashExecutionBindingOrSpaceCannotWrite() {
        assertThatThrownBy(() -> service.create(request("ORIGINAL_TEXT", "71", "c".repeat(64), "UNKNOWN", false))).isInstanceOf(BusinessException.class);
        assignment.setExecutionId(99L); assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class);
        assignment.setExecutionId(71L); assignment.setBindingHash("c".repeat(64)); assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class);
        assignment.setBindingHash("a".repeat(64)); assignment.setSpaceId(99L); assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class);
        verifyNoInteractions(persistence);
    }
    @Test void explicitCaseScoreMismatchAndBlindedClaimAreRejectedBeforeOriginalRead() {
        var req = request();
        assertThatThrownBy(() -> service.create(new EvaluationFeedbackCreateDTO(10L, 1L, null, null, req.label(), req.score(), null, req.onlineQuality()))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(new EvaluationFeedbackCreateDTO(10L, null, null, null, req.label(), BigDecimal.ONE, null, req.onlineQuality()))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(request("ORIGINAL_TEXT", "71", text.contentHash(), "BLINDED", false))).hasMessageContaining("BLIND_REVIEW_NOT_READY");
        verifyNoInteractions(agents, persistence);
    }
    @Test void severeEventRequiresOwnerAndOrdinaryZeroDoesNotTriggerIt() {
        doThrow(new BusinessException(403, "非OWNER")).when(access).requireOwner(10L);
        assertThatThrownBy(() -> service.create(request("ORIGINAL_TEXT", "71", text.contentHash(), "NON_BLINDED", true))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(persistence); doNothing().when(access).requireOwner(10L);
        var req = request("ORIGINAL_TEXT", "71", text.contentHash(), "NON_BLINDED", true); service.create(req);
        verify(persistence).append(input, text.identityHash(), req, 501L);
    }
    @Test void completeBadStructureProposalCanReceiveZeroButMultipleOriginalsCannotBePicked() {
        var item = OnlineOriginalChangeUtils.signed(input, "81", "CHANGE_REQUEST", 3L, null, "c".repeat(64), null, false, "2026-10-12T12:00");
        when(tasks.getOnlineOriginalChanges(61L, 10L, 71L)).thenReturn(Result.ok(new OnlineOriginalChangesVO(2, "AVAILABLE", input, List.of(item))));
        var req = request("CHANGE_REQUEST", "81", item.payloadHash(), "UNKNOWN", false); service.create(req);
        verify(persistence).append(input, item.identityHash(), req, 501L); clearInvocations(persistence);
        when(tasks.getOnlineOriginalChanges(61L, 10L, 71L)).thenReturn(Result.ok(new OnlineOriginalChangesVO(2, "AVAILABLE", input, List.of(item, item))));
        assertThatThrownBy(() -> service.create(req)).hasMessageContaining("MULTIPLE_ORIGINALS_NOT_READY"); verifyNoInteractions(persistence);
    }
    @Test void draftRequiresFrozenSuccessorAndItsOwnEvidenceNamespace() {
        input = new OnlineOriginalInputVO("61", "71", "10", "20", "11", "51", "a".repeat(64), "41", "DRAFT", 3L, "b".repeat(64), true);
        when(tasks.getOnlineOriginalInput(61L, 10L, 71L)).thenReturn(Result.ok(input));
        var item = OnlineOriginalChangeUtils.signed(input, "81", "DRAFT_DOCUMENT", 3L, 4L, "c".repeat(64), "d".repeat(64), true, "2026-10-12T12:00");
        when(tasks.getOnlineOriginalChanges(61L, 10L, 71L)).thenReturn(Result.ok(new OnlineOriginalChangesVO(2, "AVAILABLE", input, List.of(item))));
        var req = request("DRAFT_DOCUMENT", "81", item.payloadHash(), "UNKNOWN", false); service.create(req);
        verify(persistence).append(input, item.identityHash(), req, 501L); clearInvocations(persistence);
        assertThatThrownBy(() -> service.create(request("CHANGE_REQUEST", "81", item.payloadHash(), "UNKNOWN", false))).isInstanceOf(BusinessException.class);
        var wrong = OnlineOriginalChangeUtils.signed(input, "81", "DRAFT_DOCUMENT", 3L, 5L, item.payloadHash(), "d".repeat(64), true, item.capturedAt());
        when(tasks.getOnlineOriginalChanges(61L, 10L, 71L)).thenReturn(Result.ok(new OnlineOriginalChangesVO(2, "AVAILABLE", input, List.of(wrong))));
        assertThatThrownBy(() -> service.create(req)).isInstanceOf(BusinessException.class); verifyNoInteractions(persistence);
    }
    @Test void currentPermissionsAndHumanIdentityRemainMandatory() {
        doThrow(new BusinessException(403, "权限不足")).when(access).requirePermission(10L, EVALUATION_RUN);
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class); verifyNoInteractions(tasks, agents, persistence);
        SecurityContextHolder.clearContext(); assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class);
    }
    @Test void validSignatureForAnotherOriginalSubjectCannotBeReused() {
        var unsigned = new AgentOnlineOriginalTextVO(2, "AVAILABLE", "71", "61", "71", "99", "20", "11", "51", "a".repeat(64),
                text.contentHash(), null, text.capturedAt(), text.originalText());
        var other = new AgentOnlineOriginalTextVO(2, "AVAILABLE", "71", "61", "71", "99", "20", "11", "51", "a".repeat(64),
                text.contentHash(), OnlineOriginalTextUtils.identityHash(unsigned), text.capturedAt(), text.originalText());
        assertThat(OnlineOriginalTextUtils.valid(other)).isTrue(); when(agents.getOnlineOriginalText(61L, 10L)).thenReturn(Result.ok(other));
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class); verifyNoInteractions(persistence);
    }
    @Test void actualAgentCapabilityCannotSubmitHumanFeedback() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test-agent").header("alg", "RS256")
                .subject("61").claim("scope", "agent").claim("actorType", "AGENT").build()));
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class);
        verifyNoInteractions(access, tasks, agents, persistence);
    }
    private OnlineOriginalChangesVO collection() {
        var good = OnlineOriginalChangeUtils.signed(input,"81","CHANGE_REQUEST",3L,null,"c".repeat(64),"d".repeat(64),true,"2026-10-12T12:00");
        var bad = OnlineOriginalChangeUtils.signed(input,"82","CHANGE_REQUEST",4L,null,"e".repeat(64),null,false,"2026-10-12T12:00");
        return new OnlineOriginalChangesVO(2,"AVAILABLE",input,List.of(good,bad));
    }
    @Test void completeCollectionIncludingWrongBaselineProposalCanBeRatedAsZero() {
        var source = collection(); when(tasks.getOnlineOriginalChanges(61L,10L,71L)).thenReturn(Result.ok(source));
        var hash = OnlineOriginalChangeUtils.setIdentityHash(source); var req = request("ORIGINAL_CHANGE_SET","51",hash,"UNKNOWN",false);
        service.create(req); verify(persistence).append(input,hash,req,501L);
        verify(tasks,times(1)).getOnlineOriginalChanges(61L,10L,71L); verify(tasks,times(2)).getOnlineOriginalInput(61L,10L,71L);
        verifyNoInteractions(agents);
    }
    @Test void callerCannotRateOnlyTheGoodPartOrChooseItsIdAsCollectionId() {
        var source = collection(); when(tasks.getOnlineOriginalChanges(61L,10L,71L)).thenReturn(Result.ok(source));
        var subset = new OnlineOriginalChangesVO(2,"AVAILABLE",input,List.of(source.items().getFirst()));
        assertThatThrownBy(() -> service.create(request("ORIGINAL_CHANGE_SET","51",OnlineOriginalChangeUtils.setIdentityHash(subset),"UNKNOWN",false))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(request("ORIGINAL_CHANGE_SET","81",OnlineOriginalChangeUtils.setIdentityHash(source),"UNKNOWN",false))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(request("CHANGE_REQUEST","81",source.items().getFirst().payloadHash(),"UNKNOWN",false))).hasMessageContaining("MULTIPLE_ORIGINALS_NOT_READY");
        verifyNoInteractions(persistence);
    }
    @Test void changedCollectionOrMissingOriginalCannotUseAnEarlierCollectionHash() {
        var source = collection(); var req = request("ORIGINAL_CHANGE_SET","51",OnlineOriginalChangeUtils.setIdentityHash(source),"UNKNOWN",false);
        when(tasks.getOnlineOriginalChanges(61L,10L,71L)).thenReturn(Result.ok(new OnlineOriginalChangesVO(2,"AVAILABLE",input,List.of(source.items().getFirst()))));
        assertThatThrownBy(() -> service.create(req)).isInstanceOf(BusinessException.class);
        when(tasks.getOnlineOriginalChanges(61L,10L,71L)).thenReturn(Result.ok(new OnlineOriginalChangesVO(2,"EVIDENCE_UNAVAILABLE",input,List.of())));
        assertThatThrownBy(() -> service.create(req)).isInstanceOf(BusinessException.class); verifyNoInteractions(persistence);
    }
    @Test void collectionReadRechecksCurrentPermissionBeforeAppendAndKeepsBlindnessGate() {
        var source = collection(); var hash = OnlineOriginalChangeUtils.setIdentityHash(source);
        when(tasks.getOnlineOriginalChanges(61L,10L,71L)).thenReturn(Result.ok(source));
        when(tasks.getOnlineOriginalInput(61L,10L,71L)).thenReturn(Result.ok(input)).thenThrow(new BusinessException(40300,"权限撤回"));
        assertThatThrownBy(() -> service.create(request("ORIGINAL_CHANGE_SET","51",hash,"UNKNOWN",false))).hasMessageContaining("撤回");
        assertThatThrownBy(() -> service.create(request("ORIGINAL_CHANGE_SET","51",hash,"BLINDED",false))).hasMessageContaining("BLIND_REVIEW_NOT_READY");
        verifyNoInteractions(persistence);
    }
    @Test void collectionProtocolStillRejectsUnknownFieldsAndFloatingRubric() {
        var req = request("ORIGINAL_CHANGE_SET","51",OnlineOriginalChangeUtils.setIdentityHash(collection()),"UNKNOWN",false);
        var json = JsonUtils.toJson(req);
        assertThat(EvaluationFeedbackCreateDTO.parse(json).onlineQuality().originalEvidenceType()).isEqualTo("ORIGINAL_CHANGE_SET");
        assertThatThrownBy(() -> EvaluationFeedbackCreateDTO.parse(json.replace("\"rubricScore\":0","\"rubricScore\":0.0"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> EvaluationFeedbackCreateDTO.parse(json.replace("\"rubricScore\":0","\"rubricScore\":0,\"items\":[\"81\"]"))).isInstanceOf(BusinessException.class);
    }
}
