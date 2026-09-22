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
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
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
                mock(SkillPackageProperties.class), new AgentTelemetry(), executionMapper);

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
                "a".repeat(64), 1, "b".repeat(64), sourceTaskId, sourceExecutionId,
                sourceHash == null ? null : 3, sourceHash,
                "http://task-service/mcp", "capability");
    }
}
