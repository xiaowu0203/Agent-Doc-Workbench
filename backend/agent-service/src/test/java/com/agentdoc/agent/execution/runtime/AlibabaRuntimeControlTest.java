package com.agentdoc.agent.execution.runtime;

import com.agentdoc.agent.execution.model.TokenUsage;
import com.agentdoc.agent.execution.tool.TokenUsageEstimator;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.common.pojo.TokenValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlibabaRuntimeControlTest {

    @Test
    void closesFailedModelCallWithoutInventingUsage() {
        AgentEntity agent = new AgentEntity();
        agent.setTokenBudget(10L);
        agent.setMaxIterations(1);
        AlibabaRuntimeControl control = new AlibabaRuntimeControl(agent, 10L,
                () -> false, new TokenUsageEstimator());

        control.beforeModel();
        control.afterModelFailure();

        assertThat(control.modelInFlight()).isFalse();
        assertThat(control.usage()).isEqualTo(TokenUsage.unavailable());
    }

    @Test
    void allowsContinuationWhenOneTokenDimensionIsUnknown() {
        AgentEntity agent = new AgentEntity();
        agent.setTokenBudget(10L);
        AlibabaRuntimeControl control = new AlibabaRuntimeControl(agent, 10L,
                () -> false, new TokenUsageEstimator());

        control.beforeModel();

        control.afterModel(new TokenUsage(
                TokenValue.provider(4L), TokenValue.unavailable(), TokenValue.unavailable()));

        assertThat(control.usage().input()).isEqualTo(TokenValue.provider(4L));
    }

    @Test
    void recordsPartialUsageWhenModelIsCanceled() {
        AgentEntity agent = new AgentEntity();
        agent.setTokenBudget(10L);
        AlibabaRuntimeControl control = new AlibabaRuntimeControl(agent, 10L,
                () -> true, new TokenUsageEstimator());

        control.afterModelCanceled(new TokenUsage(
                TokenValue.provider(4L), TokenValue.unavailable(), TokenValue.provider(3L)));

        assertThat(control.usage().input()).isEqualTo(TokenValue.provider(4L));
        assertThat(control.usage().output()).isEqualTo(TokenValue.provider(3L));
        assertThat(control.modelInFlight()).isFalse();
    }

    @Test
    void tripsBudgetWhenKnownTokenLowerBoundExceedsBudget() {
        AgentEntity agent = new AgentEntity();
        agent.setTokenBudget(10L);
        AlibabaRuntimeControl control = new AlibabaRuntimeControl(agent, 10L,
                () -> false, new TokenUsageEstimator());

        control.beforeModel();

        assertThatThrownBy(() -> control.afterModel(new TokenUsage(
                TokenValue.provider(11L), TokenValue.unavailable(), TokenValue.unavailable())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("预算已超限");
    }

    @Test
    void allowsUnboundedExecutionWhenNoTaskOrAgentBudgetIsConfigured() {
        AgentEntity agent = new AgentEntity();
        AlibabaRuntimeControl control = new AlibabaRuntimeControl(agent, null,
                () -> false, new TokenUsageEstimator());

        control.beforeModel();
        control.afterModel(new TokenUsage(
                TokenValue.provider(4L), TokenValue.unavailable(), TokenValue.provider(7L)));

        control.beforeModel();
    }

    @Test
    void stopsBeforeAnotherModelCallWhenBudgetIsExactlyConsumed() {
        AgentEntity agent = new AgentEntity();
        AlibabaRuntimeControl control = new AlibabaRuntimeControl(agent, 10L,
                () -> false, new TokenUsageEstimator());

        control.beforeModel();
        control.afterModel(new TokenUsage(
                TokenValue.provider(4L), TokenValue.unavailable(), TokenValue.provider(6L)));

        assertThatThrownBy(control::beforeModel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("预算已耗尽");
    }

    @Test
    void reportsIterationLimitWithDedicatedException() {
        AgentEntity agent = new AgentEntity();
        agent.setMaxIterations(1);
        AlibabaRuntimeControl control = new AlibabaRuntimeControl(agent, 10L,
                () -> false, new TokenUsageEstimator());

        control.beforeModel();
        control.afterModelFailure();
        control.beforeModel();
        control.afterModelFailure();

        assertThatThrownBy(control::beforeModel)
                .isInstanceOf(AgentExecutionLimitExceededException.class)
                .hasMessageContaining("最大迭代次数");
    }
}
