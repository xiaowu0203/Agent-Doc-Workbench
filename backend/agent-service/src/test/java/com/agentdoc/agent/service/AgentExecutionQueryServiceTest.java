package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.mapper.AgentExecutionModelCallMapper;
import com.agentdoc.agent.mapper.AgentExecutionToolCallMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionToolCallEntity;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.AgentExecutionAuditVO;
import com.agentdoc.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentExecutionQueryServiceTest {

    private final AgentExecutionMapper executionMapper = mock(AgentExecutionMapper.class);
    private final AgentExecutionModelCallMapper modelCallMapper = mock(AgentExecutionModelCallMapper.class);
    private final AgentExecutionToolCallMapper toolCallMapper = mock(AgentExecutionToolCallMapper.class);
    private final SpaceAccessService spaceAccessService = mock(SpaceAccessService.class);
    private final AgentExecutionQueryService service = new AgentExecutionQueryService(
            executionMapper, modelCallMapper, toolCallMapper, spaceAccessService);

    @Test
    void returnsNullBeforeAgentExecutionIsCreated() {
        when(executionMapper.selectOne(any())).thenReturn(null);

        assertThat(service.getByWorkbenchTask(11L, 7L)).isNull();

        verify(spaceAccessService).requirePermission(7L, TASK_READ);
    }

    @Test
    void rejectsExecutionFromAnotherSpace() {
        AgentExecutionEntity execution = new AgentExecutionEntity();
        execution.setSpaceId(8L);
        when(executionMapper.selectOne(any())).thenReturn(execution);

        assertThatThrownBy(() -> service.getByWorkbenchTask(11L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void returnsOnlySanitizedSnapshotsAndOrderedAuditData() {
        AgentExecutionEntity execution = execution();
        AgentExecutionToolCallEntity toolCall = new AgentExecutionToolCallEntity();
        toolCall.setSequenceNo(1);
        toolCall.setToolName("skill_read_instructions");
        toolCall.setToolSource("SKILL_LOCAL");
        toolCall.setToolSourceKey("skill-local");
        toolCall.setSkillVersionId(22L);
        toolCall.setStatus("SUCCEEDED");
        toolCall.setStartedAt(LocalDateTime.now());
        when(executionMapper.selectOne(any())).thenReturn(execution);
        when(modelCallMapper.selectList(any())).thenReturn(List.of());
        when(toolCallMapper.selectList(any())).thenReturn(List.of(toolCall));

        AgentExecutionAuditVO result = service.getByWorkbenchTask(11L, 7L);

        assertThat(result.agentName()).isEqualTo("审计 Agent");
        assertThat(result.model().displayName()).isEqualTo("GPT Test");
        assertThat(result.skill().boundSkills()).singleElement()
                .satisfies(skill -> assertThat(skill.skillVersionId()).isEqualTo(22L));
        assertThat(result.toolCalls()).singleElement()
                .satisfies(call -> assertThat(call.skillVersionId()).isEqualTo(22L));
        assertThat(result.model().getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("baseUrl");
        assertThat(result.skill().boundSkills().getFirst().getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("storageKey", "instructionText");
    }

    private AgentExecutionEntity execution() {
        AgentExecutionEntity execution = new AgentExecutionEntity();
        execution.setId(3L);
        execution.setWorkbenchTaskId(11L);
        execution.setSpaceId(7L);
        execution.setAgentId(9L);
        execution.setAgentNameSnapshot("审计 Agent");
        execution.setAgentConfigVersion(4L);
        execution.setMaxIterations(12);
        execution.setExecutionTimeoutSeconds(300);
        execution.setStatus("WORKING");
        execution.setModelSnapshot(JsonUtils.toJson(Map.of(
                "id", 5L, "modelKey", "gpt-test", "baseUrl", "https://secret.example")));
        execution.setModelDisplayNameSnapshot("GPT Test");
        execution.setModelConfigVersion(6L);
        execution.setSkillSnapshotJson("""
                [{"skillId":21,"skillVersionId":22,"versionNo":3,"name":"audit-skill",
                  "activationDescription":"审计文档","sha256":"package-hash",
                  "storageKey":"storage/secret","instructionText":"secret instruction"}]
                """);
        execution.setSelectedSkillVersionIdsJson("[22]");
        execution.setToolDefinitionSnapshotJson("[]");
        execution.setExternalMcpSnapshotJson("[]");
        return execution;
    }
}
