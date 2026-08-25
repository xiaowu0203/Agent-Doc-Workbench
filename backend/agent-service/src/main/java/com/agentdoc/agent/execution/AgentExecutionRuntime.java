package com.agentdoc.agent.execution;

import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;

import java.util.function.BooleanSupplier;

public interface AgentExecutionRuntime {

    AgentRuntimeResult execute(AgentEntity agent, ModelEntity model, String instruction,
                               AgentTaskInputDTO input, BooleanSupplier cancelRequested);
}
