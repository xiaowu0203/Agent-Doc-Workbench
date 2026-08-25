package com.agentdoc.agent.execution.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent-doc.agent.runtime")
public record AgentRuntimeProperties(AgentRuntimeType type) {

    public AgentRuntimeProperties {
        type = type == null ? AgentRuntimeType.CUSTOM : type;
    }
}
