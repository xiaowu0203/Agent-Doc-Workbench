package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentOnlineConfigFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.AgentOnlineConfigPairVO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.convertor.OnlineAssignmentConvertor;
import com.agentdoc.evaluation.convertor.OnlineExperimentConvertor;
import com.agentdoc.evaluation.evaluator.EvaluatorContractValidator;
import com.agentdoc.evaluation.mapper.*;
import com.agentdoc.evaluation.pojo.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OnlineExperimentServiceTest {
    private final OnlineExperimentMapper mapper = mock(OnlineExperimentMapper.class);
    private final OnlineAssignmentMapper assignments = mock(OnlineAssignmentMapper.class);
    private final OnlineExperimentCreateIntentMapper intents = mock(OnlineExperimentCreateIntentMapper.class);
    private final OnlineExperimentPersistenceService persistence = mock(OnlineExperimentPersistenceService.class);
    private final EvaluatorVersionMapper versions = mock(EvaluatorVersionMapper.class);
    private final SpaceAccessService access = mock(SpaceAccessService.class);
    private final AgentOnlineConfigFeign agents = mock(AgentOnlineConfigFeign.class);
    private final DocumentFeign documents = mock(DocumentFeign.class);
    private final AtomicReference<OnlineExperimentEntity> stored = new AtomicReference<>();
    private final AtomicReference<OnlineExperimentCreateIntentEntity> intent = new AtomicReference<>();
    private OnlineExperimentService service;
    private EvaluatorVersionEntity version;
    private final AgentOnlineConfigPairVO pair = new AgentOnlineConfigPairVO(
            new AgentOnlineConfigPairVO.TemplateIdentity("601", 2, "a".repeat(64), "b".repeat(64)),
            new AgentOnlineConfigPairVO.TemplateIdentity("602", 2, "c".repeat(64), "b".repeat(64)),
            "d".repeat(64), 600, 1048576L);

    @BeforeEach
    void setup() {
        var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("501")
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        service = new OnlineExperimentService(mapper, assignments, new OnlineAssignmentConvertor(), new OnlineExperimentConvertor(), intents, persistence,
                versions, new EvaluatorContractValidator(), access, agents, documents, mock(OnlinePreflightProofService.class));
        when(intents.selectOne(any())).thenAnswer(call -> intent.get());
        doAnswer(call -> { intent.set(call.getArgument(0)); return null; }).when(persistence).reserve(any());
        doAnswer(call -> { stored.set(call.getArgument(0)); return null; }).when(persistence).complete(any());
        when(mapper.selectById(anyLong())).thenAnswer(call -> stored.get());
        when(assignments.participation(anyList())).thenReturn(List.of());
        when(documents.getDocumentRefs(anyList())).thenReturn(Result.ok(List.of(
                new DocumentRefVO(301L, 101L, "doc1"), new DocumentRefVO(302L, 101L, "doc2"))));
        when(agents.prepareOnlineConfigs(any())).thenReturn(Result.ok(pair));
        when(agents.onlineDependency(anyLong(), eq(101L))).thenReturn(Result.ok(pair.dependencyHash()));
        version = new EvaluatorVersionEntity();
        version.setId(401L); version.setSpaceId(101L); version.setStatus("PUBLISHED");
        version.setEvaluatorKey("online-original-text-assertion");
        version.setConfigSchemaVersion(1); version.setResultSchemaVersion(1); version.setConfigJson("{}");
        version.setImplementationVersion("online-contract-v2");
        version.setContentHash(StableSnapshotUtils.snapshotHash(1, Map.of("evaluatorKey", version.getEvaluatorKey(),
                "configSchemaVersion", 1, "config", Map.of(), "resultSchemaVersion", 1, "implementationVersion", "online-contract-v2")));
        when(versions.selectBatchIds(any())).thenReturn(List.of(version));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void createsPrivateFrozenDraftAndDuplicateRequestDoesNotCaptureAgain() {
        var first = service.create(OnlineExperimentRequestValidatorTest.request());
        var second = service.create(OnlineExperimentRequestValidatorTest.request());
        assertThat(first.summary().id()).isEqualTo(second.summary().id());
        assertThat(first.summary().manifestSchemaVersion()).isEqualTo(2);
        assertThat(first.summary().status()).isEqualTo("CREATED");
        assertThat(first.activeSlot()).isNull();
        String json = JsonUtils.toJson(first);
        assertThat(json).doesNotContain("candidateAgentPrompt", "expectedBindings", "assertions", "候选提示词");
        var projection = JsonUtils.parseStrict(json, JsonNode.class);
        assertThat(projection.path("id").asText()).isEqualTo(first.summary().id());
        assertThat(projection.has("summary")).isFalse();
        verify(agents, times(1)).prepareOnlineConfigs(any());
        verify(persistence, times(1)).complete(any());
    }

    @Test
    void failureAfterIntentUsesSameExperimentIdentityOnRetry() {
        when(agents.prepareOnlineConfigs(any())).thenReturn(Result.fail(ErrorCode.INTERNAL_ERROR, "unavailable"), Result.ok(pair));
        assertThatThrownBy(() -> service.create(OnlineExperimentRequestValidatorTest.request())).isInstanceOf(BusinessException.class);
        long reservedId = intent.get().getId();
        var created = service.create(OnlineExperimentRequestValidatorTest.request());
        assertThat(created.summary().id()).isEqualTo(Long.toString(reservedId));
        verify(persistence, times(1)).reserve(any());
    }

    @Test
    void incompleteRemoteTemplateProofIsRejectedBeforeManifestIsWritten() {
        when(agents.prepareOnlineConfigs(any())).thenReturn(Result.ok(new AgentOnlineConfigPairVO(
                new AgentOnlineConfigPairVO.TemplateIdentity("601", 2, null, "b".repeat(64)),
                pair.candidate(), pair.dependencyHash(), 600, 1048576L)));
        assertThatThrownBy(() -> service.create(OnlineExperimentRequestValidatorTest.request())).hasMessageContaining("TEMPLATE_INVALID");
        verify(persistence, never()).complete(any());
    }

    @Test
    void sameActorKeyWithChangedPayloadIsConflictBeforeNetworkCalls() {
        service.create(OnlineExperimentRequestValidatorTest.request());
        var json = JsonUtils.toJson(OnlineExperimentRequestValidatorTest.request()).replace("候选提示词", "新提示词");
        assertThatThrownBy(() -> service.create(OnlineExperimentRequestValidator.parse(json))).hasMessageContaining("IDEMPOTENCY_CONFLICT");
        verify(agents, times(1)).prepareOnlineConfigs(any());
    }

    @Test
    void ownerGateRunsBeforeIntentOrConfigurationSideEffects() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "OWNER_REQUIRED")).when(access).requireOwner(101L);
        assertThatThrownBy(() -> service.create(OnlineExperimentRequestValidatorTest.request())).hasMessageContaining("OWNER_REQUIRED");
        verifyNoInteractions(intents, persistence, agents, documents, versions);
    }

    @Test
    void rejectsForeignDocumentAndUnpublishedRuleWithoutCapturingTemplates() {
        when(documents.getDocumentRefs(anyList())).thenReturn(Result.ok(List.of(
                new DocumentRefVO(301L, 101L, "doc1"), new DocumentRefVO(302L, 999L, "foreign"))));
        assertThatThrownBy(() -> service.create(OnlineExperimentRequestValidatorTest.request())).hasMessageContaining("SCOPE_DRIFT");
        when(documents.getDocumentRefs(anyList())).thenReturn(Result.ok(List.of(
                new DocumentRefVO(301L, 101L, "doc1"), new DocumentRefVO(302L, 101L, "doc2"))));
        version.setStatus("DRAFT");
        assertThatThrownBy(() -> service.create(OnlineExperimentRequestValidatorTest.request())).hasMessageContaining("ONLINE_RULE_NOT_READY");
        verifyNoInteractions(agents);
    }

    @Test
    void preflightShowsDraftValidButNeverStartableAndUsesBigIntegerPlannedBudget() {
        var created = service.create(OnlineExperimentRequestValidatorTest.request());
        var preflight = service.preflight(created.summary().id());
        assertThat(preflight.draftEligible()).isTrue();
        assertThat(preflight.startable()).isFalse();
        assertThat(preflight.plannedUpperTokenBudget()).isEqualTo("10000");
        assertThat(preflight.srm().status()).isEqualTo("NOT_ENOUGH_UNITS");
        assertThat(preflight.issues()).extracting(value -> value.code())
                .contains("ONLINE_EXECUTION_NOT_READY", "ONLINE_RULE_NOT_READY", "ONLINE_REPORT_NOT_READY");
    }

    @Test
    void preflightDetectsDependencyAndVersionDriftWithoutRewritingManifest() {
        var created = service.create(OnlineExperimentRequestValidatorTest.request());
        String frozen = stored.get().getManifestJson();
        when(agents.onlineDependency(anyLong(), eq(101L))).thenReturn(Result.ok("e".repeat(64)));
        version.setContentHash("f".repeat(64));
        var checked = service.preflight(created.summary().id());
        assertThat(checked.draftEligible()).isFalse();
        assertThat(checked.startable()).isFalse();
        assertThat(checked.issues()).extracting(value -> value.code()).contains("DEPENDENCY_DRIFT", "ONLINE_RULE_NOT_READY");
        assertThat(stored.get().getManifestJson()).isEqualTo(frozen);
    }

    @Test
    void legacySchemaStaysReadableAndPreflightIsRejectedWithoutNetworkReinterpretation() {
        service.create(OnlineExperimentRequestValidatorTest.request());
        stored.get().setManifestSchemaVersion(1);
        stored.get().setManifestJson("legacy-content-kept-verbatim");
        reset(agents);
        var detail = service.detail(stored.get().getId().toString());
        assertThat(detail.summary().reasonCodes()).contains("UNSUPPORTED_ONLINE_SCHEMA");
        var checked = service.preflight(stored.get().getId().toString());
        assertThat(checked.draftEligible()).isFalse();
        assertThat(checked.startable()).isFalse();
        verifyNoInteractions(agents);
        assertThat(stored.get().getManifestJson()).isEqualTo("legacy-content-kept-verbatim");
    }
}
