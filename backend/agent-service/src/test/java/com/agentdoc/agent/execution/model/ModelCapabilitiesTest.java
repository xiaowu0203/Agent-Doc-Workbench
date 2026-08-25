package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.enums.ModelAdapterType;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelCapabilitiesTest {

    @Test
    void rejectsMcpTaskWhenAdapterDoesNotSupportToolCalling() {
        ModelAdapter adapter = new ModelAdapter() {
            @Override
            public Set<ModelAdapterType> supportedTypes() {
                return Set.of(ModelAdapterType.OPENAI_CHAT);
            }

            @Override
            public ModelCapabilities capabilities() {
                return new ModelCapabilities(false, false);
            }

            @Override
            public ModelTurnResult callOnce(ModelAdapterContext context, List<Message> messages) {
                throw new UnsupportedOperationException();
            }
        };
        ModelAdapterRegistry registry = new ModelAdapterRegistry(List.of(adapter));
        ModelEntity model = new ModelEntity();
        model.setProvider("openai");
        model.setAdapterType(ModelAdapterType.OPENAI_CHAT.getCode());

        assertThrows(BusinessException.class,
                () -> registry.require(model, ModelCapabilities.requiredToolCalling()));
    }
}
