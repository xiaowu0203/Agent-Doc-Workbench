package com.agentdoc.agent.service;

import com.agentdoc.agent.config.SkillPackageProperties;
import com.agentdoc.agent.execution.application.ExecutionPreparationTransactionService;
import com.agentdoc.agent.execution.context.SkillExecutionSnapshot;
import com.agentdoc.agent.execution.prompt.PromptService;
import com.agentdoc.agent.mapper.AgentOnlineConfigMapper;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.pojo.entity.AgentOnlineConfigEntity;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.AgentOnlineConfigPrepareDTO;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentOnlineConfigServiceTest {
    private final AgentOnlineConfigMapper mapper = mock(AgentOnlineConfigMapper.class);
    private final AgentOnlineConfigPersistenceService persistence = mock(AgentOnlineConfigPersistenceService.class);
    private final ExecutionPreparationTransactionService capture = mock(ExecutionPreparationTransactionService.class);
    private final SkillSnapshotService skills = mock(SkillSnapshotService.class);
    private final DocumentFeign documents = mock(DocumentFeign.class);
    private final SpaceAccessService access = mock(SpaceAccessService.class);
    private final List<AgentOnlineConfigEntity> stored = new ArrayList<>();
    private AgentOnlineConfigService service;
    private AgentEntity agent;
    private ModelEntity model;

    @BeforeEach
    void setup() {
        var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("501")
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var prompt = new PromptService(new ByteArrayResource("platform".getBytes(StandardCharsets.UTF_8)));
        service = new AgentOnlineConfigService(mapper, persistence, capture, skills, prompt, new SkillPackageProperties(), access, documents);
        agent = new AgentEntity();
        agent.setId(201L); agent.setSpaceId(101L); agent.setConfigVersion(1L);
        agent.setSystemPrompt("baseline"); agent.setSkillSelectionMode("ALL_BOUND"); agent.setExternalMcpEnabled(false);
        agent.setExecutionTimeoutSeconds(600); agent.setTokenBudget(1000L); agent.setMaxIterations(10); agent.setDocScope("SPACE");
        model = new ModelEntity();
        model.setId(11L); model.setConfigVersion(1L); model.setModelKey("test-model"); model.setProvider("test");
        model.setAdapterType("OPENAI"); model.setBaseUrl("https://model.invalid"); model.setOptionsJson("{}");
        model.setEncryptedApiKey("private-ciphertext");
        when(documents.checkSpaceOwner(101L)).thenReturn(Result.ok());
        when(mapper.selectList(any())).thenAnswer(call -> List.copyOf(stored));
        when(capture.capture(201L)).thenAnswer(call -> new ExecutionPreparationTransactionService.CapturedExecution(agent, model, List.of(), List.of()));
        when(skills.snapshot(any(), anyList(), any())).thenReturn(new SkillExecutionSnapshot(
                List.of(), List.of(), List.of(), null, "[]", "a".repeat(64), "", "ALL_BOUND", null));
        doAnswer(call -> { stored.addAll(call.getArgument(0)); return null; }).when(persistence).savePair(anyList());
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private AgentOnlineConfigPrepareDTO request() {
        return new AgentOnlineConfigPrepareDTO("901", "101", "201", "a".repeat(64), "candidate");
    }

    @Test
    void capturesCurrentConfigurationOnceAndAtomicallyStoresOnlyPromptDifference() {
        var pair = service.prepare(request());
        verify(capture, times(1)).capture(201L);
        verify(persistence, times(1)).savePair(anyList());
        assertThat(stored).hasSize(2);
        assertThat(pair.baseline().nonPromptHash()).isEqualTo(pair.candidate().nonPromptHash());
        assertThat(pair.baseline().hash()).isNotEqualTo(pair.candidate().hash());
        assertThat(stored).allSatisfy(value -> assertThat(value.getTemplateJson())
                .doesNotContain("private-ciphertext", "sourceTaskId", "userInstruction", "encryptedApiKey"));
        agent.setSystemPrompt("changed-current");
        assertThat(service.prepare(request())).isEqualTo(pair);
        verify(capture, times(1)).capture(201L);
    }

    @Test
    void refusesSameExperimentWithChangedCandidateAndTamperedProof() {
        service.prepare(request());
        assertThatThrownBy(() -> service.prepare(new AgentOnlineConfigPrepareDTO("901", "101", "201", "a".repeat(64), "different")))
                .isInstanceOf(BusinessException.class);
        stored.forEach(value -> value.setNonPromptHash("b".repeat(64)));
        assertThatThrownBy(() -> service.prepare(request())).isInstanceOf(BusinessException.class);
        verify(persistence, times(1)).savePair(anyList());
    }

    @Test
    void ownerFailureBlocksCaptureEvenIfOtherPermissionsPass() {
        when(documents.checkSpaceOwner(101L)).thenReturn(Result.fail(ErrorCode.FORBIDDEN, "denied"));
        assertThatThrownBy(() -> service.prepare(request())).hasMessageContaining("OWNER_REQUIRED");
        verifyNoInteractions(capture, persistence);
    }

    @Test
    void rejectsRouterAndSecretOptionsBeforePersistence() {
        agent.setSkillSelectionMode("ROUTER");
        assertThatThrownBy(() -> service.prepare(request())).hasMessageContaining("TEMPLATE_INVALID");
        agent.setSkillSelectionMode("ALL_BOUND");
        model.setOptionsJson("{\"headers\":{\"api-key\":\"private\"}}");
        assertThatThrownBy(() -> service.prepare(request())).hasMessageContaining("TEMPLATE_INVALID");
        verifyNoInteractions(persistence);
    }

    @Test
    void tokenLimitsRemainConfigurationWhileAuthenticationTokensAreRejected() {
        model.setOptionsJson("{\"temperature\":0.7,\"maxTokens\":500}");
        service.prepare(request());
        assertThat(stored.getFirst().getTemplateJson()).contains("\"maxTokens\":500", "\"temperature\":\"0.7\"");
        stored.clear();
        model.setOptionsJson("{\"accessToken\":\"private\"}");
        assertThatThrownBy(() -> service.prepare(request())).hasMessageContaining("TEMPLATE_INVALID");
        verify(persistence, times(1)).savePair(anyList());
    }

    @Test
    void currentDependencyDriftIsReportedWithoutChangingFrozenTemplates() {
        var pair = service.prepare(request());
        String frozen = stored.getFirst().getTemplateJson();
        model.setConfigVersion(2L);
        assertThat(service.dependency(901L, 101L)).isNotEqualTo(pair.dependencyHash());
        assertThat(stored.getFirst().getTemplateJson()).isEqualTo(frozen);
    }
}
