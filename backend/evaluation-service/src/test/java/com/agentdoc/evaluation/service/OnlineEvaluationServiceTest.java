package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.AgentOnlineOriginalTextVO;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.evaluator.DeterministicEvaluationOutcome;
import com.agentdoc.evaluation.evaluator.OnlineOriginalTextEvaluator;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.dto.OnlineEvaluationCreateDTO;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineEvaluationAttemptEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnlineEvaluationServiceTest {
    private final OnlineExperimentMapper experiments = mock(OnlineExperimentMapper.class);
    private final OnlineAssignmentMapper assignments = mock(OnlineAssignmentMapper.class);
    private final EvaluatorVersionMapper versions = mock(EvaluatorVersionMapper.class);
    private final SpaceAccessService access = mock(SpaceAccessService.class);
    private final TaskFeign tasks = mock(TaskFeign.class);
    private final AgentFeign agents = mock(AgentFeign.class);
    private final OnlineEvaluationPersistenceService persistence = mock(OnlineEvaluationPersistenceService.class);
    private final OnlineOriginalTextEvaluator evaluator = spy(new OnlineOriginalTextEvaluator());
    private final OnlineEvaluationService service = new OnlineEvaluationService(experiments, assignments, versions, access, tasks, agents, evaluator, persistence);
    private final OnlineEvaluationCreateDTO request = new OnlineEvaluationCreateDTO("evaluate-1", "quality");
    private OnlineAssignmentEntity assignment;
    private OnlineExperimentEntity experiment;
    private EvaluatorVersionEntity version;
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "RS256")
                .subject("501").claim("scope", "user").build()));
        assignment = new OnlineAssignmentEntity(); assignment.setId(51L); assignment.setExperimentId(11L); assignment.setSpaceId(10L);
        assignment.setTaskId(61L); assignment.setExecutionId(71L); assignment.setAgentId(20L); assignment.setDocumentId(41L);
        assignment.setBindingSchemaVersion(2); assignment.setBindingHash("a".repeat(64)); when(assignments.selectById(51L)).thenReturn(assignment);
        version = new EvaluatorVersionEntity(); version.setId(81L); version.setSpaceId(10L); version.setStatus("PUBLISHED");
        version.setEvaluatorKey("online-original-text-assertion"); version.setConfigSchemaVersion(1); version.setResultSchemaVersion(1); version.setConfigJson("{}");
        version.setImplementationVersion(OnlineOriginalTextEvaluator.IMPLEMENTATION_VERSION); version.setContentHash(OnlineExperimentService.versionHash(version));
        when(versions.selectById(81L)).thenReturn(version);
        experiment = new OnlineExperimentEntity(); experiment.setId(11L); experiment.setSpaceId(10L); experiment.setManifestSchemaVersion(2);
        var expected = Map.of("assertions", List.of(Map.of("field", "originalText", "operator", "EXACT", "value", "冻结期望")));
        experiment.setManifestJson(OnlineProtocolUtils.canonical("online.manifest", Map.of("ruleBindings", List.of(Map.of("evaluatorKey", version.getEvaluatorKey(),
                "contentHash", version.getContentHash(), "binding", Map.of("ruleKey", "quality", "evaluatorVersionId", "81", "evidenceTarget", "ORIGINAL_TEXT",
                        "expectedBindings", List.of(Map.of("documentId", "41", "expectedJson", expected))))))));
        experiment.setManifestHash(StableSnapshotUtils.sha256Utf8(experiment.getManifestJson())); when(experiments.selectById(11L)).thenReturn(experiment);
        when(tasks.checkOriginalEvidencePermission(61L,10L,71L)).thenReturn(Result.ok());
        when(agents.getOnlineOriginalText(61L,10L)).thenReturn(Result.ok(new AgentOnlineOriginalTextVO(2,"EVIDENCE_UNAVAILABLE",null,"61","71","10","20","11","51",
                assignment.getBindingHash(),null,null,null,null)));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void missingCaptureAppendsSkippedAndNoQualityZeroUsingFrozenExpected() {
        service.evaluate("11","51",request); var outcome = ArgumentCaptor.forClass(DeterministicEvaluationOutcome.class);
        verify(persistence).append(eq(assignment),eq(81L),eq("quality"),eq("evaluate-1"),anyString(),anyString(),eq(501L),any(),outcome.capture());
        assertThat(outcome.getValue().status().name()).isEqualTo("SKIPPED"); assertThat(outcome.getValue().metrics()).isEmpty();
        verify(evaluator).evaluate(eq("{}"),argThat(value -> value.contains("冻结期望")),any());
        verify(access).requirePermission(10L,"evaluation:run");
    }
    @Test void permissionsAndImmutableVersionAreCheckedBeforeOriginalBody() {
        when(tasks.checkOriginalEvidencePermission(61L,10L,71L)).thenReturn(Result.fail(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.evaluate("11","51",request)).isInstanceOf(BusinessException.class); verifyNoInteractions(agents);
        when(tasks.checkOriginalEvidencePermission(61L,10L,71L)).thenReturn(Result.ok()); version.setContentHash("c".repeat(64));
        assertThatThrownBy(() -> service.evaluate("11","51",request)).isInstanceOf(BusinessException.class); verifyNoInteractions(agents);
    }
    @Test void responseIdentityMismatchCannotAppendOrChangeOriginalExecution() {
        when(agents.getOnlineOriginalText(61L,10L)).thenReturn(Result.ok(new AgentOnlineOriginalTextVO(2,"EVIDENCE_UNAVAILABLE",null,"62","71","10","20","11","51",
                assignment.getBindingHash(),null,null,null,null)));
        assertThatThrownBy(() -> service.evaluate("11","51",request)).isInstanceOf(BusinessException.class);
        verify(persistence,never()).append(any(),any(),any(),any(),any(),any(),any(),any(),any());
    }
    @Test void historicalContractOnlyVersionAndTamperedConfigCannotRunNewEngine() {
        version.setImplementationVersion("online-contract-v2");
        assertThatThrownBy(() -> service.evaluate("11","51",request)).isInstanceOf(BusinessException.class); verifyNoInteractions(agents);
        version.setImplementationVersion(OnlineOriginalTextEvaluator.IMPLEMENTATION_VERSION); version.setConfigJson("{\"unexpected\":true}");
        assertThatThrownBy(() -> service.evaluate("11","51",request)).isInstanceOf(BusinessException.class); verifyNoInteractions(agents);
    }
    @Test void requestRetryReusesFirstResultBeforeReadingOrReevaluatingBody() {
        var prior = new OnlineEvaluationAttemptEntity(); when(persistence.prior(51L,81L,"evaluate-1")).thenReturn(prior);
        service.evaluate("11","51",request); verify(persistence).reuse(eq(prior),anyString()); verifyNoInteractions(agents,evaluator);
    }
    @Test void evaluatorErrorIsAppendedAsErrorWithoutChangingExecution() {
        doThrow(new IllegalStateException("不得进入结果或日志的秘密")).when(evaluator).evaluate(anyString(),anyString(),any());
        service.evaluate("11","51",request); var outcome = ArgumentCaptor.forClass(DeterministicEvaluationOutcome.class);
        verify(persistence).append(any(),any(),any(),any(),any(),any(),any(),any(),outcome.capture());
        assertThat(outcome.getValue().status().name()).isEqualTo("ERROR"); assertThat(outcome.getValue().metrics()).isEmpty(); assertThat(outcome.getValue().detailsJson()).isEqualTo("{}");
    }
    @Test void strictCreateRequestCannotSupplyExpectedOrCoerceTypes() {
        assertThatThrownBy(() -> OnlineEvaluationCreateDTO.parse("{\"clientRequestKey\":\"x\",\"ruleKey\":\"quality\",\"expected\":{}}"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> OnlineEvaluationCreateDTO.parse("{\"clientRequestKey\":1,\"ruleKey\":\"quality\"}"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> OnlineEvaluationCreateDTO.parse("{\"clientRequestKey\":\"x\",\"ruleKey\":\"quality\",\"ruleKey\":\"other\"}"))
                .isInstanceOf(BusinessException.class);
    }
}
