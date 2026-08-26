package com.agentdoc.agent.execution;

import com.agentdoc.agent.execution.model.ModelAdapterRegistry;
import com.agentdoc.agent.execution.runtime.AgentRuntimeProperties;
import com.agentdoc.agent.execution.runtime.SpringAiAgentExecutionRuntime;
import com.agentdoc.agent.execution.runtime.SpringAiAlibabaAgentExecutionRuntime;
import com.agentdoc.agent.execution.tool.ProviderNeutralToolLoop;
import com.agentdoc.agent.execution.tool.TokenUsageEstimator;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentRuntimeSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RuntimeConfiguration.class);

    @Test
    void defaultsToCustomRuntime() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SpringAiAgentExecutionRuntime.class);
            assertThat(context).doesNotHaveBean(SpringAiAlibabaAgentExecutionRuntime.class);
        });
    }

    @Test
    void selectsAlibabaRuntime() {
        contextRunner.withPropertyValues("agent-doc.agent.runtime.type=spring-ai-alibaba")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SpringAiAgentExecutionRuntime.class);
                    assertThat(context).hasSingleBean(SpringAiAlibabaAgentExecutionRuntime.class);
        });
    }

    @Test
    void acceptsEnumLiteralForAlibabaRuntime() {
        contextRunner.withPropertyValues("agent-doc.agent.runtime.type=SPRING_AI_ALIBABA")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SpringAiAgentExecutionRuntime.class);
                    assertThat(context).hasSingleBean(SpringAiAlibabaAgentExecutionRuntime.class);
                });
    }

    @Test
    void invalidRuntimeTypeFailsBinding() {
        contextRunner.withPropertyValues("agent-doc.agent.runtime.type=unknown")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AgentRuntimeProperties.class)
    @Import({SpringAiAgentExecutionRuntime.class, SpringAiAlibabaAgentExecutionRuntime.class})
    static class RuntimeConfiguration {
        @Bean AgentConfigCryptoService cryptoService() { return mock(AgentConfigCryptoService.class); }
        @Bean ModelAdapterRegistry modelAdapterRegistry() { return mock(ModelAdapterRegistry.class); }
        @Bean ProviderNeutralToolLoop providerNeutralToolLoop() { return mock(ProviderNeutralToolLoop.class); }
        @Bean TokenUsageEstimator tokenUsageEstimator() { return new TokenUsageEstimator(); }
    }
}
