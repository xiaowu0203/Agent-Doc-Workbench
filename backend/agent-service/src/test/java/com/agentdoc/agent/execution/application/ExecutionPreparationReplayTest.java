package com.agentdoc.agent.execution.application;

import com.agentdoc.agent.config.SkillPackageProperties;
import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.convertor.AgentExecutionConvertor;
import com.agentdoc.agent.execution.prompt.PromptService;
import com.agentdoc.agent.execution.skill.SkillSelectionStrategyRegistry;
import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.observability.AgentTelemetry;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.service.SkillSnapshotService;
import com.agentdoc.agent.service.AgentCandidateConfigService;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.SnapshotCanonicalV3Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import com.agentdoc.common.enums.TaskExecutionMode;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionPreparationReplayTest {

    @Test
    void restoresFrozenConfigurationAndOnlyResolvesCurrentCredential() {
        ExecutionPreparationTransactionService transactionService =
                mock(ExecutionPreparationTransactionService.class);
        AgentExecutionPersistenceService persistenceService = mock(AgentExecutionPersistenceService.class);
        AgentExecutionMapper executionMapper = mock(AgentExecutionMapper.class);
        AgentExecutionEntity source = sourceExecution();
        ModelEntity currentCredential = new ModelEntity();
        currentCredential.setId(2L);
        currentCredential.setEncryptedApiKey("current-encrypted-secret");
        currentCredential.setModelKey("changed-model");
        currentCredential.setConfigVersion(99L);
        when(executionMapper.selectById(9L)).thenReturn(source);
        when(transactionService.resolveReplayModelCredential(2L)).thenReturn(currentCredential);
        ExecutionPreparationService service = new ExecutionPreparationService(
                transactionService, persistenceService, mock(SkillSnapshotService.class),
                mock(SkillSelectionStrategyRegistry.class), mock(PromptService.class),
                mock(SkillPackageProperties.class), new AgentTelemetry(), executionMapper,
                mock(AgentCandidateConfigService.class));

        AgentTaskInputDTO replayInput = input(11L, TaskExecutionMode.ISOLATED.name(), 10L, 9L,
                source.getExecutionSnapshotHash());
        ExecutionPreparationService.PreparedExecution prepared = service.prepare(
                "replay-a2a", "replay-context", replayInput, "frozen instruction");

        assertThat(prepared.model().getEncryptedApiKey()).isEqualTo("current-encrypted-secret");
        assertThat(prepared.model().getModelKey()).isEqualTo("frozen-model");
        assertThat(prepared.model().getConfigVersion()).isEqualTo(7L);
        assertThat(prepared.execution().getExecutionSnapshotHash())
                .isEqualTo(source.getExecutionSnapshotHash());
        assertThat(prepared.externalMcpConnections()).isEmpty();
        verify(persistenceService).insertSubmitted(prepared.execution());
    }

    @Test
    void restoresExperimentCandidateAndKeepsPerCaseInstructionPromptHash() {
        ExecutionPreparationTransactionService transactionService =
                mock(ExecutionPreparationTransactionService.class);
        AgentExecutionPersistenceService persistenceService = mock(AgentExecutionPersistenceService.class);
        AgentExecutionMapper executionMapper = mock(AgentExecutionMapper.class);
        AgentCandidateConfigService candidateService = mock(AgentCandidateConfigService.class);
        PromptService promptService = mock(PromptService.class);
        AgentExecutionEntity source = sourceExecution();
        when(executionMapper.selectById(9L)).thenReturn(source);
        ModelEntity currentCredential = new ModelEntity();
        currentCredential.setId(2L);
        currentCredential.setEncryptedApiKey("current-encrypted-secret");
        when(transactionService.resolveReplayModelCredential(2L)).thenReturn(currentCredential);

        JsonNode envelope = JsonUtils.parse(source.getExecutionSnapshotJson(), JsonNode.class);
        ObjectNode candidateSnapshot = ((ObjectNode) envelope.get("snapshot")).deepCopy();
        candidateSnapshot.put("systemPrompt", "candidate prompt");
        String candidateJson = SnapshotCanonicalV3Utils.canonicalEnvelope(3, candidateSnapshot);
        String candidateHash = SnapshotCanonicalV3Utils.hashEnvelope(candidateJson);
        when(candidateService.restore(77L, 30L, candidateHash)).thenReturn(
                new AgentCandidateConfigService.RestoredCandidateConfig(77L, 30L, 1L,
                        10L, 9L, source.getExecutionSnapshotHash(), 3, candidateHash,
                        "candidate prompt", "representative-prompt-hash", candidateJson, candidateSnapshot));
        when(promptService.hash("candidate prompt", "frozen instruction"))
                .thenReturn("case-prompt-hash");
        ExecutionPreparationService service = new ExecutionPreparationService(
                transactionService, persistenceService, mock(SkillSnapshotService.class),
                mock(SkillSelectionStrategyRegistry.class), promptService,
                mock(SkillPackageProperties.class), new AgentTelemetry(), executionMapper, candidateService);
        AgentTaskInputDTO input = new AgentTaskInputDTO(11L, 1L, 30L, 40L, 1000L,
                TaskExecutionMode.ISOLATED.name(), 5L, "a".repeat(64), 1, "b".repeat(64),
                "f".repeat(64), 10L, 9L, 3, source.getExecutionSnapshotHash(), 77L, 3, candidateHash,
                "http://task-service/mcp", "capability");

        ExecutionPreparationService.PreparedExecution prepared = service.prepare(
                "experiment-a2a", "experiment-context", input, "frozen instruction");

        assertThat(prepared.systemPrompt()).isEqualTo("candidate prompt");
        assertThat(prepared.execution().getPromptHash()).isEqualTo("case-prompt-hash");
        assertThat(prepared.execution().getExecutionSnapshotHash()).isEqualTo(candidateHash);
        assertThat(prepared.externalMcpConnections()).isEmpty();
        verify(persistenceService).insertSubmitted(prepared.execution());
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
        AgentExecutionEntity source = AgentExecutionConvertor.toEntity(
                "source-a2a", "source-context", input(10L, TaskExecutionMode.LIVE.name(), null, null, null),
                agent, model, "frozen prompt", "prompt-hash");
        source.setId(9L);
        source.setUserInstructionSnapshot("frozen instruction");
        source.setSkillSnapshotJson("[]");
        source.setSkillInstructionHash("skill-hash");
        source.setSkillSelectionMode("ALL_BOUND");
        source.setSkillSelectionEffectiveMode("ALL_BOUND");
        source.setSelectedSkillVersionIdsJson("[]");
        source.setToolWhitelistSnapshot("[]");
        source.setExternalMcpSnapshotJson("[]");
        source.setExecutionSnapshotSchemaVersion(AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION);
        source.setExecutionSnapshotJson(AgentExecutionConvertor.snapshotJson(source));
        source.setExecutionSnapshotHash(AgentExecutionConvertor.snapshotHash(source));
        return source;
    }

    private AgentTaskInputDTO input(Long taskId, String mode, Long sourceTaskId,
                                    Long sourceExecutionId, String sourceHash) {
        return new AgentTaskInputDTO(taskId, 1L, 30L, 40L, 1000L, mode, 5L,
                "a".repeat(64), 1, "b".repeat(64), null, sourceTaskId, sourceExecutionId,
                sourceHash == null ? null : 3, sourceHash,
                null, null, null,
                "http://task-service/mcp", "capability");
    }
}
