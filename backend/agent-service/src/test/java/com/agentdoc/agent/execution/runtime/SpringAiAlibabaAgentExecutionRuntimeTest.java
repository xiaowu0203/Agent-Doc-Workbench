package com.agentdoc.agent.execution.runtime;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiAlibabaAgentExecutionRuntimeTest {

    @Test
    void propagatesCancellationFromWrappedGraphStreamError() {
        AgentExecutionCanceledException cancellation = new AgentExecutionCanceledException();

        assertThatThrownBy(() -> Flux.error(new IllegalStateException(cancellation))
                .onErrorMap(SpringAiAlibabaAgentExecutionRuntime::unwrapCancellation)
                .blockLast())
                .isSameAs(cancellation);
    }
}
