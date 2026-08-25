package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import org.springframework.ai.tool.ToolCallback;

import java.util.Collections;
import java.util.List;

/**
 * 单次模型调用所需的运行时上下文，不包含持久化或厂商专属字段。
 */
public record ModelAdapterContext(
        AgentEntity agent,
        ModelEntity model,
        String apiKey,
        Integer maxOutputTokens,
        List<ToolCallback> toolCallbacks) {

    public ModelAdapterContext withMaxOutputTokens(Integer value) {
        return new ModelAdapterContext(agent, model, apiKey, value, toolCallbacks);
    }

    public ModelAdapterContext forConnectivityTest() {
        return new ModelAdapterContext(agent, model, apiKey, 1, Collections.emptyList());
    }

    @Override
    public String toString() {
        return "ModelAdapterContext[agentId=" + idOf(agent) +
                ", modelId=" + idOf(model) +
                ", apiKey=" + (apiKey == null ? "null" : "<redacted>") +
                ", maxOutputTokens=" + maxOutputTokens +
                ", toolCallbackCount=" + (toolCallbacks == null ? 0 : toolCallbacks.size()) + "]";
    }

    private Long idOf(Object entity) {
        if (entity instanceof com.agentdoc.common.pojo.entity.BaseEntity baseEntity) {
            return baseEntity.getId();
        }
        return null;
    }
}
