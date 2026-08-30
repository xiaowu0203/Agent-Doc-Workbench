package com.agentdoc.agent.convertor;

import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.enums.SkillSelectionMode;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentConvertorTest {

    @Test
    void rejectsInvalidPersistedToolWhitelistJson() {
        AgentEntity entity = new AgentEntity();
        entity.setSkillSelectionMode(SkillSelectionMode.ALL_BOUND.name());
        entity.setStatus(AgentStatus.ENABLED.getCode());
        entity.setToolWhitelist("not-json");

        assertThatThrownBy(() -> AgentConvertor.toVO(entity))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("工具白名单 JSON 无效");
    }
}
