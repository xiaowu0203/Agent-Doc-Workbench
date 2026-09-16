package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.execution.runtime.AgentExecutionCanceledException;
import com.agentdoc.common.logging.SensitiveFieldPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskScopedMcpToolsTest {

    @Test
    void rejectsRelativeMcpUrlsWithoutLeakingCapability() {
        String capability = "task-capability-secret";

        assertThatThrownBy(() -> TaskScopedMcpTools.open("/mcp", capability, 1, () -> false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("绝对地址")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain(capability));
    }

    @Test
    void checksCancellationBeforeInitializingMcpClient() {
        assertThatThrownBy(() -> TaskScopedMcpTools.open("http://localhost/mcp", "capability", 1, () -> true, null))
                .isInstanceOf(AgentExecutionCanceledException.class);
    }

    @Test
    void createsIsolatedPolicyForDynamicQueryCredential() {
        SensitiveFieldPolicy first = TaskScopedMcpTools.externalSensitiveFieldPolicy("custom-key", "test-value");
        SensitiveFieldPolicy second = TaskScopedMcpTools.externalSensitiveFieldPolicy("other-key", "test-value");

        assertThat(first.isSensitive("custom_key", "value")).isTrue();
        assertThat(first.isSensitive("other_key", "value")).isFalse();
        assertThat(second.isSensitive("other_key", "value")).isTrue();
    }

    @Test
    void rejectsQueryCredentialWithoutRegisteredFieldName() {
        assertThatThrownBy(() -> TaskScopedMcpTools.externalSensitiveFieldPolicy(null, "test-value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("MCP Query API Key 参数名不能为空")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("test-value"));
    }
}
