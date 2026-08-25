package com.agentdoc.agent.execution.runtime;

import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface AgentExecutionRuntime {

    AgentRuntimeResult execute(AgentEntity agent, ModelEntity model, String instruction,
                               AgentTaskInputDTO input, BooleanSupplier cancelRequested);

    default AgentRuntimeResult execute(AgentEntity agent, ModelEntity model, String instruction,
                                       AgentTaskInputDTO input, BooleanSupplier cancelRequested,
                                       Consumer<String> onTextDelta) {
        return execute(agent, model, instruction, input, cancelRequested);
    }
}
