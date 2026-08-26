package com.agentdoc.agent.execution.runtime;

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
}
