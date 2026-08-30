package com.agentdoc.agent.execution.skill;

import com.agentdoc.agent.config.SkillSelectionProperties;
import com.agentdoc.agent.execution.model.ModelAdapter;
import com.agentdoc.agent.execution.model.ModelAdapterRegistry;
import com.agentdoc.agent.execution.model.ModelTurnResult;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.agent.service.ModelService;
import com.agentdoc.common.exception.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouterSkillSelectionStrategyTest {

    @Test
    void acceptsOnlyBoundIdsAndRestoresSkillIdOrder() {
        Fixture fixture = fixture("{\"skillVersionIds\":[200,100]}");

        SkillSelectionResult result = fixture.strategy.select(fixture.context);

        assertThat(result.effectiveMode()).isEqualTo("ROUTER");
        assertThat(result.selectedSkills()).extracting(SkillCandidate::skillVersionId)
                .containsExactly(100L, 200L);
        assertThat(result.routerSnapshotJson()).contains("inputSha256").doesNotContain("task text");
    }

    @Test
    void fallsBackToAllBoundOnInvalidResponse() {
        Fixture fixture = fixture("{\"skillVersionIds\":[999]}");

        SkillSelectionResult result = fixture.strategy.select(fixture.context);

        assertThat(result.effectiveMode()).isEqualTo("ROUTER_FALLBACK");
        assertThat(result.selectedSkills()).hasSize(2);
        assertThat(result.routerSnapshotJson()).contains("fallbackReason").doesNotContain("999");
    }

    @Test
    void rejectsRouterModeWhenHistoricalDescriptionWasNotBackfilled() {
        Fixture fixture = fixture("{\"skillVersionIds\":[]}");
        SkillCandidate invalid = new SkillCandidate(1L, 100L, 1, "first", null,
                "sha", "key", "body", List.of(), List.of());

        assertThatThrownBy(() -> fixture.strategy.select(new SkillSelectionContext(
                "task", fixture.context.agent(), fixture.context.model(), List.of(invalid))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void doesNotFallbackOnAdapterConfigurationError() {
        Fixture fixture = fixture("{\"skillVersionIds\":[]}");
        when(fixture.registry.require(fixture.context.model()))
                .thenThrow(new IllegalStateException("adapter configuration error"));

        assertThatThrownBy(() -> fixture.strategy.select(fixture.context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration");
    }

    private Fixture fixture(String response) {
        SkillSelectionProperties properties = new SkillSelectionProperties();
        ModelService modelService = mock(ModelService.class);
        ModelAdapterRegistry registry = mock(ModelAdapterRegistry.class);
        AgentConfigCryptoService crypto = mock(AgentConfigCryptoService.class);
        ModelAdapter adapter = mock(ModelAdapter.class);
        ModelEntity model = new ModelEntity();
        model.setId(7L);
        model.setModelKey("router-model");
        model.setEncryptedApiKey("encrypted");
        AgentEntity agent = new AgentEntity();
        agent.setId(8L);
        when(registry.require(model)).thenReturn(adapter);
        when(crypto.decrypt("encrypted")).thenReturn("plain");
        when(adapter.callOnce(any(), any())).thenReturn(new ModelTurnResult(null, response, null));
        RouterSkillSelectionStrategy strategy = new RouterSkillSelectionStrategy(
                properties, modelService, registry, crypto, new SimpleMeterRegistry());
        List<SkillCandidate> candidates = List.of(
                candidate(1L, 100L, "first"), candidate(2L, 200L, "second"));
        return new Fixture(strategy, registry,
                new SkillSelectionContext("task text", agent, model, candidates));
    }

    private SkillCandidate candidate(long skillId, long versionId, String name) {
        return new SkillCandidate(skillId, versionId, 1, name, name + " description",
                name + " sha", name + " key", name + " body", List.of(), List.of());
    }

    private record Fixture(RouterSkillSelectionStrategy strategy, ModelAdapterRegistry registry,
                           SkillSelectionContext context) {
    }
}
