package com.agentdoc.agent.service;

import com.agentdoc.agent.config.SkillPackageProperties;
import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.convertor.AgentExecutionConvertor;
import com.agentdoc.agent.execution.application.ExecutionPreparationTransactionService;
import com.agentdoc.agent.execution.prompt.PromptService;
import com.agentdoc.agent.mapper.AgentCandidateConfigMapper;
import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.pojo.entity.AgentCandidateConfigEntity;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.AgentCandidateConfigCreateDTO;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.common.feign.vo.AgentCandidateConfigVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.SnapshotCanonicalV3Utils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentCandidateConfigServiceTest {

    private AgentCandidateConfigMapper candidateMapper;
    private AgentExecutionMapper executionMapper;
    private SpaceAccessService spaceAccessService;
    private SkillSnapshotService skillSnapshotService;
    private ExecutionPreparationTransactionService transactionService;
    private AgentCandidateConfigService service;
    private AgentExecutionEntity source;

    @BeforeEach
    void setUp() {
        candidateMapper = mock(AgentCandidateConfigMapper.class);
        executionMapper = mock(AgentExecutionMapper.class);
        spaceAccessService = mock(SpaceAccessService.class);
        skillSnapshotService = mock(SkillSnapshotService.class);
        transactionService = mock(ExecutionPreparationTransactionService.class);
        PromptService promptService = new PromptService(
                new ByteArrayResource("platform".getBytes(StandardCharsets.UTF_8)));
        service = new AgentCandidateConfigService(candidateMapper, executionMapper, spaceAccessService,
                skillSnapshotService, promptService, new SkillPackageProperties(), transactionService);
        source = sourceExecution();
        when(executionMapper.selectById(9L)).thenReturn(source);
        when(skillSnapshotService.catalogPromptSection(anyList())).thenReturn("");
        when(candidateMapper.insert(any(AgentCandidateConfigEntity.class))).thenAnswer(invocation -> {
            invocation.<AgentCandidateConfigEntity>getArgument(0).setId(20L);
            return 1;
        });
        login(1001L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsCanonicalCandidateWithOnlySystemPromptChanged() {
        assertThat(source.getSpaceId()).isEqualTo(1L);
        assertThat(source.getWorkbenchTaskId()).isEqualTo(10L);
        assertThat(source.getExecutionSnapshotSchemaVersion()).isEqualTo(3);
        assertThat(source.getUserInstructionSnapshot()).isNotNull();
        assertThat(source.getExecutionSnapshotHash()).isEqualTo(request().sourceSnapshotHash());
        AgentCandidateConfigVO result = service.create(request());

        ArgumentCaptor<AgentCandidateConfigEntity> captor =
                ArgumentCaptor.forClass(AgentCandidateConfigEntity.class);
        verify(candidateMapper).insert(captor.capture());
        AgentCandidateConfigEntity stored = captor.getValue();
        JsonNode sourceSnapshot = JsonUtils.parse(source.getExecutionSnapshotJson(), JsonNode.class).get("snapshot");
        JsonNode candidateSnapshot = JsonUtils.parse(stored.getExecutionSnapshotJson(), JsonNode.class).get("snapshot");
        ObjectNode sourceWithoutPrompt = ((ObjectNode) sourceSnapshot).deepCopy();
        ObjectNode candidateWithoutPrompt = ((ObjectNode) candidateSnapshot).deepCopy();
        sourceWithoutPrompt.remove("systemPrompt");
        candidateWithoutPrompt.remove("systemPrompt");

        assertThat(result.candidateConfigId()).isEqualTo(20L);
        assertThat(result.promptDiffFieldPaths()).containsExactly("snapshot.systemPrompt");
        assertThat(result.candidateSnapshotHash()).isNotEqualTo(result.sourceSnapshotHash());
        assertThat(sourceWithoutPrompt).isEqualTo(candidateWithoutPrompt);
        assertThat(candidateSnapshot.get("systemPrompt").asText()).isEqualTo("platform\n\ncandidate");
        assertThat(AgentCandidateConfigVO.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("agentPrompt", "systemPrompt", "executionSnapshotJson", "promptHash");
    }

    @Test
    void rejectsTamperedCandidateDuringRestore() {
        AgentCandidateConfigEntity stored = createAndCapture();
        String tampered = stored.getExecutionSnapshotJson().replace("frozen-model", "tampered-model");
        stored.setExecutionSnapshotJson(tampered);
        when(candidateMapper.selectById(20L)).thenReturn(stored);

        assertThatThrownBy(() -> service.restore(20L, 1L, stored.getCandidateSnapshotHash()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("完整性校验失败");
    }

    @Test
    void rejectsNonPromptDifferenceEvenWhenCandidateHashIsRecomputed() {
        AgentCandidateConfigEntity stored = createAndCapture();
        JsonNode envelope = JsonUtils.parse(stored.getExecutionSnapshotJson(), JsonNode.class);
        ObjectNode candidate = (ObjectNode) envelope.get("snapshot");
        ((ObjectNode) candidate.get("model")).put("modelKey", "tampered-model");
        String tamperedJson = SnapshotCanonicalV3Utils.canonicalEnvelope(3, candidate);
        stored.setExecutionSnapshotJson(tamperedJson);
        stored.setCandidateSnapshotHash(SnapshotCanonicalV3Utils.hashEnvelope(tamperedJson));
        when(candidateMapper.selectById(20L)).thenReturn(stored);

        assertThatThrownBy(() -> service.restore(20L, 1L, stored.getCandidateSnapshotHash()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("候选执行快照存在非 Prompt 差异");
    }

    @Test
    void returnsExistingCandidateForSameIdempotentRequest() {
        AgentCandidateConfigEntity stored = createAndCapture();
        when(candidateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(stored);

        AgentCandidateConfigVO repeated = service.create(request());

        assertThat(repeated.candidateConfigId()).isEqualTo(20L);
        verify(candidateMapper, times(1)).insert(any(AgentCandidateConfigEntity.class));
    }

    @Test
    void rejectsCrossSpaceRestoreBeforeReadingSource() {
        AgentCandidateConfigEntity stored = createAndCapture();
        when(candidateMapper.selectById(20L)).thenReturn(stored);

        assertThatThrownBy(() -> service.restore(20L, 2L, stored.getCandidateSnapshotHash()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("候选配置身份无效");
    }

    @Test
    void rejectsInvalidSourceSnapshot() {
        source.setExecutionSnapshotJson(source.getExecutionSnapshotJson().replace("frozen-model", "tampered-model"));

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("完整性校验失败");
        verify(candidateMapper, never()).insert(any(AgentCandidateConfigEntity.class));
    }

    @Test
    void rejectsRestoreWhenCurrentCredentialIsMissing() {
        AgentCandidateConfigEntity stored = createAndCapture();
        when(candidateMapper.selectById(20L)).thenReturn(stored);
        ModelEntity currentModel = new ModelEntity();
        currentModel.setId(2L);
        when(transactionService.resolveReplayModelCredential(2L)).thenReturn(currentModel);

        assertThatThrownBy(() -> service.restore(20L, 1L, stored.getCandidateSnapshotHash()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("候选配置模型凭证不可用");
    }

    @Test
    void restoresFrozenCandidateWhenProofAndCredentialRemainValid() {
        AgentCandidateConfigEntity stored = createAndCapture();
        when(candidateMapper.selectById(20L)).thenReturn(stored);
        ModelEntity currentModel = new ModelEntity();
        currentModel.setId(2L);
        currentModel.setEncryptedApiKey("encrypted-secret");
        when(transactionService.resolveReplayModelCredential(2L)).thenReturn(currentModel);

        AgentCandidateConfigService.RestoredCandidateConfig restored =
                service.restore(20L, 1L, stored.getCandidateSnapshotHash());

        assertThat(restored.systemPrompt()).isEqualTo("platform\n\ncandidate");
        assertThat(restored.snapshot().get("systemPrompt").asText()).isEqualTo(restored.systemPrompt());
        assertThat(restored.candidateSnapshotHash()).isEqualTo(stored.getCandidateSnapshotHash());
    }

    private AgentCandidateConfigEntity createAndCapture() {
        service.create(request());
        ArgumentCaptor<AgentCandidateConfigEntity> captor =
                ArgumentCaptor.forClass(AgentCandidateConfigEntity.class);
        verify(candidateMapper).insert(captor.capture());
        return captor.getValue();
    }

    private AgentCandidateConfigCreateDTO request() {
        return new AgentCandidateConfigCreateDTO("experiment:1:candidate:prompt-a", 1L, 10L, 9L,
                AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION,
                source.getExecutionSnapshotHash(), "candidate");
    }

    private AgentExecutionEntity sourceExecution() {
        AgentEntity agent = new AgentEntity();
        agent.setId(1L);
        agent.setName("frozen-agent");
        agent.setConfigVersion(5L);
        agent.setMaxIterations(4);
        agent.setExecutionTimeoutSeconds(60);
        ModelEntity model = new ModelEntity();
        model.setId(2L);
        model.setProvider("openai");
        model.setAdapterType("OPENAI");
        model.setModelKey("frozen-model");
        model.setDisplayName("Frozen Model");
        model.setBaseUrl("https://example.com");
        model.setOptionsJson("{\"temperature\":0}");
        model.setConfigVersion(7L);
        model.setContextWindow(10000L);
        model.setMaxOutputTokens(1000L);
        model.setInputPricePerMillion(new BigDecimal("1.20"));
        model.setOutputPricePerMillion(new BigDecimal("2.30"));
        AgentExecutionEntity execution = AgentExecutionConvertor.toEntity(
                "source-a2a", "source-context", input(), agent, model,
                "platform\n\nsource", "source-prompt-hash");
        execution.setId(9L);
        execution.setUserInstructionSnapshot("frozen instruction");
        execution.setSkillSnapshotJson("[]");
        execution.setSkillInstructionHash("skill-hash");
        execution.setSkillSelectionMode("ALL_BOUND");
        execution.setSkillSelectionEffectiveMode("ALL_BOUND");
        execution.setSelectedSkillVersionIdsJson("[]");
        execution.setToolWhitelistSnapshot("[]");
        execution.setExternalMcpSnapshotJson("[]");
        execution.setExecutionSnapshotSchemaVersion(AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION);
        execution.setExecutionSnapshotJson(AgentExecutionConvertor.snapshotJson(execution));
        execution.setExecutionSnapshotHash(AgentExecutionConvertor.snapshotHash(execution));
        return execution;
    }

    private AgentTaskInputDTO input() {
        return new AgentTaskInputDTO(10L, 1L, 1L, 40L, 1000L, TaskExecutionMode.LIVE.name(), 5L,
                "a".repeat(64), 1, "b".repeat(64), null, null, null, null, null,
                null, null, null,
                "http://task-service/mcp", "capability");
    }

    private void login(long userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(userId))
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, null, List.of()));
    }
}
