package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.execution.audit.AgentExecutionToolAuditService;
import com.agentdoc.agent.pojo.entity.AgentExecutionToolCallEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditingToolCallbackTest {

    @Test
    void storesHashesAndSizesWithoutPersistingPayloads() throws Exception {
        AgentExecutionToolAuditService auditService = mock(AgentExecutionToolAuditService.class);
        AgentExecutionToolCallEntity audit = new AgentExecutionToolCallEntity();
        audit.setId(9L);
        when(auditService.start(eq(7L), eq(1), eq("demo"), eq("SKILL_LOCAL"),
                eq("skill-local"), isNull(),
                eq(sha256("secret input")), eq(12L))).thenReturn(audit);
        ToolCallback delegate = new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("demo").description("demo")
                        .inputSchema("{\"type\":\"object\"}").build();
            }
            @Override public String call(String input) { return "secret result"; }
        };
        AuditingToolCallback callback = new AuditingToolCallback(delegate, 7L, "SKILL_LOCAL",
                "skill-local", null,
                new AtomicInteger(), auditService);

        assertThat(callback.call("secret input")).isEqualTo("secret result");

        verify(auditService).succeed(audit, sha256("secret result"), 13L);
    }

    @Test
    void doesNotInvokeToolWhenStartAuditFails() {
        AgentExecutionToolAuditService auditService = mock(AgentExecutionToolAuditService.class);
        when(auditService.start(eq(7L), eq(1), eq("demo"), eq("SKILL_LOCAL"),
                eq("skill-local"), isNull(), anyString(), anyLong()))
                .thenThrow(new IllegalStateException("audit unavailable"));
        AtomicInteger invocations = new AtomicInteger();
        ToolCallback delegate = new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("demo").description("demo")
                        .inputSchema("{\"type\":\"object\"}").build();
            }
            @Override public String call(String input) {
                invocations.incrementAndGet();
                return "result";
            }
        };
        AuditingToolCallback callback = new AuditingToolCallback(delegate, 7L, "SKILL_LOCAL",
                "skill-local", null, new AtomicInteger(), auditService);

        assertThatThrownBy(() -> callback.call("input"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audit unavailable");
        assertThat(invocations).hasValue(0);
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
