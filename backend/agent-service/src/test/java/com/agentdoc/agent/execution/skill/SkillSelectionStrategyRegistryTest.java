package com.agentdoc.agent.execution.skill;

import com.agentdoc.agent.enums.SkillSelectionMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillSelectionStrategyRegistryTest {

    @Test
    void resolvesStrategyByAgentMode() {
        SkillSelectionStrategy allBound = strategy(SkillSelectionMode.ALL_BOUND);
        SkillSelectionStrategy router = strategy(SkillSelectionMode.ROUTER);
        SkillSelectionStrategyRegistry registry = new SkillSelectionStrategyRegistry(List.of(allBound, router));

        assertThat(registry.require("ALL_BOUND")).isSameAs(allBound);
        assertThat(registry.require("ROUTER")).isSameAs(router);
        assertThatThrownBy(() -> registry.require("UNKNOWN"))
                .isInstanceOf(IllegalStateException.class);
    }

    private SkillSelectionStrategy strategy(SkillSelectionMode mode) {
        return new SkillSelectionStrategy() {
            @Override
            public SkillSelectionMode mode() {
                return mode;
            }

            @Override
            public SkillSelectionResult select(SkillSelectionContext context) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
