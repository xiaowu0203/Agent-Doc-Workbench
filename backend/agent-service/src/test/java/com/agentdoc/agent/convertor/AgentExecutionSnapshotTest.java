package com.agentdoc.agent.convertor;

import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentExecutionSnapshotTest {

    @Test
    void taskIdentityAndRuntimeResultDoNotChangeSnapshotHash() {
        AgentExecutionEntity first = execution();
        AgentExecutionEntity second = execution();
        second.setId(999L);
        second.setA2aTaskId("another-a2a-task");
        second.setA2aContextId("another-context");
        second.setWorkbenchTaskId(888L);
        second.setSpaceId(777L);
        second.setUserInstructionSnapshot("different business input");
        second.setStatus("FAILED");
        second.setInputTokens(100L);
        second.setOutputTokens(20L);

        assertThat(AgentExecutionConvertor.snapshotHash(second))
                .isEqualTo(AgentExecutionConvertor.snapshotHash(first));
    }

    @Test
    void resolvedConfigurationChangeChangesSnapshotHash() {
        AgentExecutionEntity first = execution();
        AgentExecutionEntity second = execution();
        second.setToolDefinitionSnapshotJson("[{\"name\":\"document_read\",\"description\":\"changed\"}]");

        assertThat(AgentExecutionConvertor.snapshotHash(second))
                .isNotEqualTo(AgentExecutionConvertor.snapshotHash(first));
    }

    @Test
    void frozenAgentIdentityChangesSnapshotHash() {
        AgentExecutionEntity first = execution();
        AgentExecutionEntity second = execution();
        second.setAgentId(666L);

        assertThat(AgentExecutionConvertor.snapshotHash(second))
                .isNotEqualTo(AgentExecutionConvertor.snapshotHash(first));
    }

    @Test
    void jsonObjectPropertyOrderDoesNotChangeSnapshotHash() {
        AgentExecutionEntity first = execution();
        AgentExecutionEntity second = execution();
        second.setModelSnapshot("{\"inputPricePerMillion\":\"1.000000\",\"id\":3,\"modelKey\":\"demo\"}");

        assertThat(AgentExecutionConvertor.snapshotHash(second))
                .isEqualTo(AgentExecutionConvertor.snapshotHash(first));
    }

    private AgentExecutionEntity execution() {
        AgentExecutionEntity entity = new AgentExecutionEntity();
        entity.setId(1L);
        entity.setA2aTaskId("a2a-task");
        entity.setA2aContextId("context");
        entity.setWorkbenchTaskId(2L);
        entity.setSpaceId(4L);
        entity.setAgentId(5L);
        entity.setAgentNameSnapshot("agent");
        entity.setAgentConfigVersion(6L);
        entity.setMaxIterations(8);
        entity.setExecutionTimeoutSeconds(60);
        entity.setSystemPromptSnapshot("system prompt");
        entity.setUserInstructionSnapshot("instruction");
        entity.setModelSnapshot("{\"modelKey\":\"demo\",\"id\":3,\"inputPricePerMillion\":\"1.000000\"}");
        entity.setModelConfigVersion(7L);
        entity.setSkillSnapshotJson("[]");
        entity.setSkillInstructionHash("skill-hash");
        entity.setSkillSelectionMode("ALL_BOUND");
        entity.setSkillSelectionEffectiveMode("ALL_BOUND");
        entity.setSelectedSkillVersionIdsJson("[2,1]");
        entity.setSkillRouterSnapshotJson("null");
        entity.setToolWhitelistSnapshot("[\"document_read\"]");
        entity.setToolDefinitionSnapshotJson("[{\"description\":\"read\",\"name\":\"document_read\"}]");
        entity.setExternalMcpSnapshotJson("[]");
        entity.setStatus("COMPLETED");
        return entity;
    }
}
