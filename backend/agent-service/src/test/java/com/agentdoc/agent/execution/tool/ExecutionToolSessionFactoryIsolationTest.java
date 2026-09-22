package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.constant.McpConstant;
import com.agentdoc.agent.execution.application.AgentExecutionPersistenceService;
import com.agentdoc.agent.execution.audit.AgentExecutionToolAuditService;
import com.agentdoc.agent.execution.context.AgentRuntimeContext;
import com.agentdoc.agent.execution.context.ExternalMcpConnection;
import com.agentdoc.agent.observability.AgentTelemetry;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.agent.security.McpEndpointSecurityValidator;
import com.agentdoc.agent.skill.storage.SkillResourceLoader;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.common.feign.dto.ExecutionArtifactAppendDTO;
import com.agentdoc.common.feign.vo.ExecutionArtifactAppendVO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import com.agentdoc.common.enums.TaskExecutionMode;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionToolSessionFactoryIsolationTest {

    @Test
    void replacesWorkbenchWriteWithArtifactCapture() {
        TaskFeign taskFeign = mock(TaskFeign.class);
        ExecutionToolSessionFactory factory = factory(taskFeign);
        ToolCallback original = mock(ToolCallback.class);
        ToolDefinition definition = ToolDefinition.builder()
                .name(McpConstant.WORKBENCH_PROPOSE_CHANGES_TOOL)
                .description("提交变更")
                .inputSchema("{\"type\":\"object\"}")
                .build();
        when(original.getToolDefinition()).thenReturn(definition);
        when(taskFeign.appendExecutionArtifact(eq(11L), eq("Bearer capability"), eq("capability"), any()))
                .thenReturn(Result.ok(new ExecutionArtifactAppendVO(
                        99L, "CHANGE_PROPOSAL", "a".repeat(64))));

        ToolCallback callback = factory.captureOnlyCallback(original, context(List.of()), new AtomicInteger());
        String result = callback.call("{\"proposal\":{\"baseVersion\":1,\"changes\":[]}}");

        assertThat(callback.getToolDefinition()).isSameAs(definition);
        assertThat(result).contains("\"captured\":true", "\"artifactId\":99");
        verify(original, never()).call(any());
        verify(taskFeign).appendExecutionArtifact(eq(11L), eq("Bearer capability"), eq("capability"),
                argThat(this::validCapture));
    }

    @Test
    void rejectsExternalMcpBeforeOpeningAnySession() {
        ExecutionToolSessionFactory factory = factory(mock(TaskFeign.class));
        ExternalMcpConnection external = new ExternalMcpConnection(
                1L, "external", "External", "https://example.com/mcp",
                "NONE", null, null, 1L, null);

        assertThatThrownBy(() -> factory.open(context(List.of(external)), () -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("隔离执行禁止初始化外部 MCP");
    }

    private boolean validCapture(ExecutionArtifactAppendDTO request) {
        return request.executionId().equals(12L)
                && request.sourceTaskId().equals(10L)
                && request.sequenceNo() == 1
                && request.artifactType().equals("CHANGE_PROPOSAL")
                && request.payloadSha256().length() == 64;
    }

    private AgentRuntimeContext context(List<ExternalMcpConnection> external) {
        AgentTaskInputDTO input = new AgentTaskInputDTO(
                11L, 20L, 30L, 40L, 1000L, TaskExecutionMode.ISOLATED.name(), 5L,
                "b".repeat(64), 1, "c".repeat(64), 10L, 9L, 3,
                "d".repeat(64), "http://task-service/mcp", "capability");
        return new AgentRuntimeContext(12L, new AgentEntity(), new ModelEntity(), input,
                "instruction", "prompt", null, List.of(), external);
    }

    private ExecutionToolSessionFactory factory(TaskFeign taskFeign) {
        return new ExecutionToolSessionFactory(
                mock(SkillResourceLoader.class), mock(AgentExecutionPersistenceService.class),
                mock(AgentExecutionToolAuditService.class), mock(AgentConfigCryptoService.class),
                mock(McpEndpointSecurityValidator.class), new AgentTelemetry(), taskFeign);
    }
}
