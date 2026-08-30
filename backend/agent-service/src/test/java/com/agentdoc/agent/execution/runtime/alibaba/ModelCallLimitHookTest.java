package com.agentdoc.agent.execution.runtime.alibaba;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCallLimitHookTest {

    @Test
    void preservesInitialModelCallWhenConfiguringIterationLimit() {
        assertThat(SpringAiAlibabaAgentExecutionRuntime.modelCallLimit(0)).isEqualTo(1);
        assertThat(SpringAiAlibabaAgentExecutionRuntime.modelCallLimit(3)).isEqualTo(4);
        assertThat(SpringAiAlibabaAgentExecutionRuntime.modelCallLimit(Integer.MAX_VALUE))
                .isEqualTo(Integer.MAX_VALUE);
    }
}
