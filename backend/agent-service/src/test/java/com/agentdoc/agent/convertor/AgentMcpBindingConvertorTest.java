package com.agentdoc.agent.convertor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentMcpBindingConvertorTest {

    @Test
    void rejectsInvalidPersistedWhitelistJson() {
        assertThatThrownBy(() -> AgentMcpBindingConvertor.parseWhitelist("not-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("白名单 JSON 无效");
    }
}
